(ns app.core-test
  (:require [clojure.test :as t]
            [clojure.data.json :as json]
            [clj-http.client :as http-client]
            [migratus.core :as migratus]
            [app.core :as core])
  (:import [java.util UUID]))

(defn reset-database-state []
  (let [database-connection (:app.services.database/connection @core/system)
        migration-config (-> (core/config :test)
                             :app.services.database/migrations
                             (assoc :db database-connection))]
    (migratus/reset migration-config)))

(defn test-system-fixture [f]
  (when (map? @core/system)
    (reset-database-state)
    (core/stop-system))
  (core/start-system :test)
  (f)
  (reset-database-state)
  (core/stop-system))

(t/use-fixtures :each test-system-fixture)

(def route-prefix  "http://localhost:3000/api")

(defn request [method route-suffix body]
  (http-client/request {:method method
                        :url (str route-prefix route-suffix)
                        :content-type :json
                        :body (json/write-str body)
                        :throw-exceptions false}))

(defn create-user [user-name]
  (request :post "/user/create" {:name user-name}))

(defn create-users [& users]
  (->> users
       (map #(create-user %))
       doall))

(defn login-user [user-name]
  (request :post "/user/login" {:name user-name}))

(defn logout-user [session-id]
  (request :post "/user/logout" {:session session-id}))

(defn promote-to-admin [user-name session-id]
  (request :post "/admin" {:user user-name :session session-id}))

(defn create-coupon [coupon-name amount discount session-id]
  (request :post "/discounts" {:name coupon-name
                               :amount amount
                               :discount discount
                               :session session-id}))

(defn apply-coupon [coupon-name session-id]
  (request :put "/discounts" {:name coupon-name :session session-id}))

(defn register-product [name price amount session-id]
  (request :post "/inventory" {:name name
                               :price price
                               :amount amount
                               :session session-id}))

(defn get-session-id [login-response]
  (-> login-response
      :body
      json/read-str
      (get "session")
      UUID/fromString))

(defn get-coupon-id [coupon-creation-response]
  (-> coupon-creation-response
      :body
      json/read-str
      (get "id")
      UUID/fromString))

(defn get-user-id [user-creation-response]
  (-> user-creation-response
      :body
      json/read-str
      (get "id")))

(defn create-first-admin-user [user-name]
  (let [_user-creation-response (create-user user-name)
        user-login-response (login-user user-name)
        session-id (get-session-id user-login-response)
        admin-promotion-response (promote-to-admin user-name session-id)]
    {:session-id session-id
     :response admin-promotion-response}))

(t/deftest new-user-creation
  (t/testing "It is possible to create a new user"
    (let [user-name "alice"
          user-creation-response (create-user user-name)]
      (t/is (= 201 (:status user-creation-response)))
      (t/is (= (str "User " user-name " created successfully")
               (:body user-creation-response))))))

(t/deftest existent-user-creation
  (t/testing "It is not possible to create an already existent user"
    (let [user-name "alice"
          _user-creation-response (create-user user-name)
          existent-user-creation-response (create-user user-name)]
      (t/is (= 409 (:status existent-user-creation-response)))
      (t/is (= (str "User " user-name " already exists; id 1")
               (:body existent-user-creation-response))))))

(t/deftest existent-user-login
  (t/testing "An existent user is able to successfully login into the system"
    (let [user-name "alice"
          _user-creation-response (create-user user-name)
          user-login-response (login-user user-name)]
      (t/is (= 200 (:status user-login-response)))
      (t/is (uuid? (get-session-id user-login-response))))))

(t/deftest nonexistent-user-login
  (t/testing "A nonexistent user is not able to login into the system"
    (let [user-name "alice"
          user-login-response (login-user user-name)]
      (t/is (= 404 (:status user-login-response)))
      (t/is (= (str "User " user-name " does not exist")
               (:body user-login-response))))))

(t/deftest logged-user-login
  (t/testing "An already logged user is not able to login a second time"
    (let [user-name "alice"
          _user-creation-response (create-user user-name)
          _user-login-response (login-user user-name)
          logged-user-login-response (login-user user-name)]
      (t/is (= 400 (:status logged-user-login-response))))))

(t/deftest logged-user-logout
  (t/testing "A logged user is able to successfully logout from the system"
    (let [user-name "alice"
          _user-creation-response (create-user user-name)
          user-login-response (login-user user-name)
          session-id (get-session-id user-login-response)
          user-logout-response (logout-user session-id)]
      (t/is (= 200 (:status user-logout-response)))
      (t/is (= (str "Session " session-id " finished") (:body user-logout-response))))))

(t/deftest finished-session-logout
  (t/testing "An already finished session cannot be finished a second time"
    (let [user-name "alice"
          _user-creation-response (create-user user-name)
          user-login-response (login-user user-name)
          session-id (get-session-id user-login-response)
          _user-logout-response (logout-user session-id)
          second-logout-response (logout-user session-id)]
      (t/is (= 400 (:status second-logout-response)))
      (t/is (= (str "Session " session-id " is not active")
               (:body second-logout-response))))))

(t/deftest nonexistent-session-logout
  (t/testing "A nonexistent session cannot be finished"
    (let [random-session-id (UUID/randomUUID)
          logout-response (logout-user random-session-id)]
      (t/is (= 404 (:status logout-response)))
      (t/is (= (str "Session " random-session-id " does not exist")
               (:body logout-response))))))

(t/deftest first-admin-promotion
  (t/testing "When there are no admins, the first user who promotes themselves to admin will succeed in doing so"
    (let [user-name "alice"
          admin-promotion (create-first-admin-user user-name)]
      (t/is (= 200 (:status (:response admin-promotion))))
      (t/is (= (str "User " user-name " has been promoted to admin")
               (:body (:response admin-promotion)))))))

(t/deftest regular-admin-promotion
  (t/testing "An admin user is able to promote a non-admin user to admin"
    (let [user-a "alice"
          user-b "bob"
          first-admin-promotion (create-first-admin-user user-a)
          _users-creation (create-user user-b)
          user-b-promotion-response (promote-to-admin user-b (:session-id first-admin-promotion))]
      (t/is (= 200 (:status user-b-promotion-response)))
      (t/is (= (str "User " user-b " has been promoted to admin")
               (:body user-b-promotion-response))))))

(t/deftest failed-first-admin-promotion
  (t/testing "A non-admin user cannot promote another user to be the first admin"
    (let [user-a "alice"
          user-b "bob"
          _users-creation (create-users user-a user-b)
          user-login-response (login-user user-a)
          session-id (get-session-id user-login-response)
          user-b-promotion-response (promote-to-admin user-b session-id)]
      (t/is (= 403 (:status user-b-promotion-response)))
      (t/is (= (str "Session " session-id " does not belong to user " user-b)
               (:body user-b-promotion-response))))))

(t/deftest failed-admin-promotion
  (t/testing "A non-admin user cannot promote another user to admin after first admin has been created"
    (let [user-a "alice"
          user-b "bob"
          user-c "charles"
          _first-admin-promotion (create-first-admin-user user-a)
          _users-creation (create-users user-b user-c)
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          second-admin-promotion-response (promote-to-admin user-c user-b-session-id)]
      (t/is (= 403 (:status second-admin-promotion-response)))
      (t/is (= (str "Session " user-b-session-id " does not belong to an admin user")
               (:body second-admin-promotion-response))))))

(t/deftest nonexistent-user-admin-promotion
  (t/testing "A nonexistent user cannot be promoted to admin"
    (let [user-a "alice"
          user-b "bob"
          admin-promotion (create-first-admin-user user-a)
          second-admin-promotion-response (promote-to-admin user-b (:session-id admin-promotion))]
      (t/is (= 404 (:status second-admin-promotion-response)))
      (t/is (= (str "User " user-b " does not exist")
               (:body second-admin-promotion-response))))))

(t/deftest new-coupon-registration
  (t/testing "An admin user is able to successfully register a new discount coupon"
    (let [user-a "alice"
          coupon-name "coupon-a"
          admin-promotion (create-first-admin-user user-a)
          coupon-creation-response (create-coupon coupon-name 1000 0.10 (:session-id admin-promotion))]
      (t/is (= 201 (:status coupon-creation-response)))
      (t/is (= (str "Discount coupon " coupon-name " created")
               (:body coupon-creation-response))))))

(t/deftest non-admin-coupon-registration
  (t/testing "A non-admin user is not able to register a new discount coupon"
    (let [user-a "alice"
          _user-creation (create-user user-a)
          login-response (login-user user-a)
          session-id (get-session-id login-response)
          coupon-creation-response (create-coupon "coupon-a" 1000 0.10 session-id)]
      (t/is (= 403 (:status coupon-creation-response)))
      (t/is (= (str "Session " session-id " does not belong to an admin user")
               (:body coupon-creation-response))))))

(t/deftest existing-coupon-registration
  (t/testing "A discount coupon cannot be registered with a coupon name already in use"
    (let [user-a "alice"
          coupon-name "coupon-a"
          admin-promotion (create-first-admin-user user-a)
          coupon-creator #(create-coupon coupon-name 1000 0.10 (:session-id admin-promotion))
          _coupon-creation-response (coupon-creator)
          existing-coupon-creation-response (coupon-creator)]
      (t/is (= 409 (:status existing-coupon-creation-response)))
      (t/is (= (str "Discount coupon " coupon-name " is already registered")
               (:body existing-coupon-creation-response))))))

(t/deftest non-positive-amount-coupo-registration
  (t/testing "A discount coupon cannot be registered with a non-positive amount"
    (let [user-a "alice"
          coupon-name "coupon-a"
          admin-promotion (create-first-admin-user user-a)]
      (t/testing "zero amount is invalid"
        (let [zero-amount 0
              coupon-creation-response (create-coupon coupon-name zero-amount 0.10 (:session-id admin-promotion))]
          (t/is (= 400 (:status coupon-creation-response)))
          (t/is (= (str "Amount must be positive. Amount sent was " zero-amount)
                   (:body coupon-creation-response)))))
      (t/testing "negative amount"
        (let [negative-amount -12
              coupon-creation-response (create-coupon coupon-name negative-amount 0.10 (:session-id admin-promotion))]
          (t/is (= 400 (:status coupon-creation-response)))
          (t/is (= (str "Amount must be positive. Amount sent was " negative-amount)
                   (:body coupon-creation-response))))))))
