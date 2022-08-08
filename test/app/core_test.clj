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

(defn delete-product [name session-id]
  (request :delete "/inventory" {:name name :session session-id}))

(defn change-product-price [name new-price session-id]
  (request :put "/inventory/price" {:name name
                                    :price new-price
                                    :session session-id}))

(defn increase-product-amount [name amount-to-increase session-id]
  (request :put "/inventory/increase" {:name name
                                       :amountToIncrease amount-to-increase
                                       :session session-id}))

(defn decrease-product-amount [name amount-to-decrease session-id]
  (request :put "/inventory/decrease" {:name name
                                       :amountToDecrease amount-to-decrease
                                       :session session-id}))

(defn add-product-to-cart [name amount session-id]
  (request :put "/cart/add" {:product name
                             :amount amount
                             :session session-id}))

(defn remove-product-from-cart [name amount session-id]
  (request :put "/cart/remove" {:product name
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

(defn get-product-id [product-creation-response]
  (-> product-creation-response
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
               (-> user-creation-response
                   :body
                   json/read-str
                   (get "message")))))))

(t/deftest existing-user-creation
  (t/testing "It is not possible to create an already existing user"
    (let [user-name "alice"
          _user-creation-response (create-user user-name)
          existing-user-creation-response (create-user user-name)]
      (t/is (= 409 (:status existing-user-creation-response)))
      (t/is (= (str "User " user-name " already exists; id 1")
               (:body existing-user-creation-response))))))

(t/deftest existing-user-login
  (t/testing "An existing user is able to successfully login into the system"
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
               (-> coupon-creation-response
                   :body
                   json/read-str
                   (get "message")))))))

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
      (t/testing "negative amount is invalid"
        (let [negative-amount -12
              coupon-creation-response (create-coupon coupon-name negative-amount 0.10 (:session-id admin-promotion))]
          (t/is (= 400 (:status coupon-creation-response)))
          (t/is (= (str "Amount must be positive. Amount sent was " negative-amount)
                   (:body coupon-creation-response))))))))

(t/deftest valid-coupon-application
  (t/testing "A valid discount coupon can be applied to a user cart"
    (let [user-a "alice"
          user-b "bob"
          coupon-name "coupon-a"
          admin-promotion (create-first-admin-user user-a)
          coupon-creation-response (create-coupon coupon-name 100 0.10 (:session-id admin-promotion))
          coupon-id (get-coupon-id coupon-creation-response)
          user-b-creation-response (create-user user-b)
          user-b-id (get-user-id user-b-creation-response)
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          coupon-application-response (apply-coupon coupon-name user-b-session-id)]
      (t/is (= 200 (:status coupon-application-response)))
      (t/is (= (str "Discount coupon " coupon-id " applied to user " user-b-id)
               (:body coupon-application-response))))))

(t/deftest nonexistent-coupon-application
  (t/testing "A nonexistent discount coupon cannot be applied to a user cart"
    (let [user-a "alice"
          _user-creation-response (create-user user-a)
          user-login-response (login-user user-a)
          session-id (get-session-id user-login-response)
          coupon-name "coupon-a"
          coupon-application-response (apply-coupon coupon-name session-id)]
      (t/is (= 404 (:status coupon-application-response)))
      (t/is (= (str "Discount coupon " coupon-name " does not exist")
               (:body coupon-application-response))))))

(t/deftest expired-coupon-application
  (t/testing "An expired discount coupon cannot be applied to a user cart"
    (let [user-a "alice"
          user-b "bob"
          user-c "charles"
          coupon-name "coupon-a"
          admin-promotion (create-first-admin-user user-a)
          coupon-creation-response (create-coupon coupon-name 1 0.10 (:session-id admin-promotion))
          coupon-id (get-coupon-id coupon-creation-response)
          _users-creation-response (create-users user-b user-c)
          user-b-login-response (login-user user-b)
          user-c-login-response (login-user user-c)
          user-b-session-id (get-session-id user-b-login-response)
          user-c-session-id (get-session-id user-c-login-response)
          _first-coupon-application-response (apply-coupon coupon-name user-b-session-id)
          second-coupon-application-response (apply-coupon coupon-name user-c-session-id)]
      (t/is (= 409 (:status second-coupon-application-response)))
      (t/is (= (str "Discount coupon " coupon-id " has expired")
               (:body second-coupon-application-response))))))

(t/deftest new-product-registration
  (t/testing "An admin user is able to register a new product into inventory"
    (let [user-a "alice"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          product-registration-response (register-product product-name 3.99 20 (:session-id admin-promotion))]
      (t/is (= 201 (:status product-registration-response)))
      (t/is (= (str "Product " product-name " registered into inventory")
               (-> product-registration-response
                   :body
                   json/read-str
                   (get "message")))))))

(t/deftest existing-product-registration
  (t/testing "An already existing product cannot be registered again"
    (let [user-a "alice"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          product-registrator #(register-product product-name 3.99 20 (:session-id admin-promotion))
          _first-product-registration-response (product-registrator)
          second-product-registration-response (product-registrator)]
      (t/is (= 409 (:status second-product-registration-response)))
      (t/is (= (str "Product " product-name " is already registered")
               (:body second-product-registration-response))))))

(t/deftest non-admin-product-registration
  (t/testing "A non-admin user is not able to register a product into inventory"
    (let [user-a "alice"
          _user-creation-response (create-user user-a)
          login-response (login-user user-a)
          session-id (get-session-id login-response)
          product-registration-response (register-product "carrot" 3.99 20 session-id)]
      (t/is (= 403 (:status product-registration-response)))
      (t/is (= (str "Session " session-id " does not belong to an admin user")
               (:body product-registration-response))))))

(t/deftest expired-admin-session-product-registration
  (t/testing "An expired admin session is not able to register a product into inventory"
    (let [user-a "alice"
          admin-promotion (create-first-admin-user user-a)
          _admin-logout (logout-user (:session-id admin-promotion))
          product-registration-response (register-product "carrot" 3.99 20 (:session-id admin-promotion))]
      (t/is (= 401 (:status product-registration-response)))
      (t/is (= (str "Session " (:session-id admin-promotion) " is not active")
               (:body product-registration-response))))))

(t/deftest existing-product-deletion
  (t/testing "An existing product can be successfully removed from inventory"
    (let [user-a "alice"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _product-registration-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          product-deletion-response (delete-product product-name (:session-id admin-promotion))]
      (t/is (= 200 (:status product-deletion-response)))
      (t/is (= (str "Product " product-name " removed from inventory")
               (:body product-deletion-response))))))

(t/deftest nonexistent-product-deletion
  (t/testing "A nonexistent product cannot be removed from inventory"
    (let [user-a "alice"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          product-deletion-response (delete-product product-name (:session-id admin-promotion))]
      (t/is (= 404 (:status product-deletion-response)))
      (t/is (= (str "Product " product-name " does not exist")
               (:body product-deletion-response))))))

(t/deftest non-admin-product-deletion
  (t/testing "A non-admin user cannot remove a product from inventory"
    (let [user-a "alice"
          user-b "bob"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _user-creation-response (create-user user-b)
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          _product-registration-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          product-deletion-response (delete-product product-name user-b-session-id)]
      (t/is (= 403 (:status product-deletion-response)))
      (t/is (= (str "Session " user-b-session-id " does not belong to an admin user")
               (:body product-deletion-response))))))

(t/deftest expired-admin-session-product-deletion
  (t/testing "An expired admin session is not able to delete a product from inventory"
    (let [user-a "alice"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          _admin-logout-response (logout-user (:session-id admin-promotion))
          product-deletion-response (delete-product product-name (:session-id admin-promotion))]
      (t/is (= 401 (:status product-deletion-response)))
      (t/is (= (str "Session " (:session-id admin-promotion) " is not active")
               (:body product-deletion-response))))))

(t/deftest existing-product-price-change
  (t/testing "An admin user is able to change the price of an existing product"
    (let [user-a "alice"
          product-name "carrot"
          new-product-price 4.99
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          product-price-change-response (change-product-price product-name new-product-price (:session-id admin-promotion))]
      (t/is (= 200 (:status product-price-change-response)))
      (t/is (= (str "Product " product-name " had its price updated to " new-product-price)
               (:body product-price-change-response))))))

(t/deftest nonexistent-product-price-change
  (t/testing "An admin user is unable to change the price of a nonexistent product"
    (let [user-a "alice"
          product-name "carrot"
          new-product-price 4.99
          admin-promotion (create-first-admin-user user-a)
          product-price-change-response (change-product-price product-name new-product-price (:session-id admin-promotion))]
      (t/is (= 404 (:status product-price-change-response)))
      (t/is (= (str "Product " product-name " does not exist")
               (:body product-price-change-response))))))

(t/deftest non-admin-product-price-change
  (t/testing "A non-admin user is unable to change the price of an existing product"
    (let [user-a "alice"
          user-b "bob"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _user-creation-response (create-user user-b)
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          _user-creation-response (create-user user-b)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          product-price-change-response (change-product-price product-name 4.99 user-b-session-id)]
      (t/is (= 403 (:status product-price-change-response)))
      (t/is (= (str "Session " user-b-session-id " does not belong to an admin user")
               (:body product-price-change-response))))))

(t/deftest expired-admin-session-product-price-change
  (t/testing "An expired admin session is unable to change the price of an existing product"
    (let [user-a "alice"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          _user-a-logout-response (logout-user (:session-id admin-promotion))
          product-price-change-response (change-product-price product-name 4.99 (:session-id admin-promotion))]
      (t/is (= 401 (:status product-price-change-response)))
      (t/is (= (str "Session " (:session-id admin-promotion) " is not active")
               (:body product-price-change-response))))))

(t/deftest existing-product-amount-increase
  (t/testing "An admin user is able to increase the available amount of an existing product"
    (let [user-a "alice"
          product-name "carrot"
          amount-to-increase 1
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          product-amount-increase-response (increase-product-amount product-name amount-to-increase (:session-id admin-promotion))]
      (t/is (= 200 (:status product-amount-increase-response)))
      (t/is (= (str "Product " product-name " amount has been increased by " amount-to-increase)
               (:body product-amount-increase-response))))))

(t/deftest nonexistent-product-amount-increase
  (t/testing "An admin user is unable to increase the available amount of a nonexistent product"
    (let [user-a "alice"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          product-amount-increase-response (increase-product-amount product-name 2 (:session-id admin-promotion))]
      (t/is (= 404 (:status product-amount-increase-response)))
      (t/is (= (str "Product " product-name " does not exist")
               (:body product-amount-increase-response))))))

(t/deftest non-admin-product-amount-increase
  (t/testing "A non-admin user is unable to increase the available amount of an existing product"
    (let [user-a "alice"
          user-b "bob"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _user-creation-response (create-user user-b)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          product-amount-increase-response (increase-product-amount product-name 2 user-b-session-id)]
      (t/is (= 403 (:status product-amount-increase-response)))
      (t/is (= (str "Session " user-b-session-id " does not belong to an admin user")
               (:body product-amount-increase-response))))))

(t/deftest expired-admin-session-product-amount-increase
  (t/testing "An expired admin session is unable to increase the available amount of an existing product"
    (let [user-a "alice"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          _user-a-logout-response (logout-user (:session-id admin-promotion))
          product-amount-increase-response (increase-product-amount product-name 2 (:session-id admin-promotion))]
      (t/is (= 401 (:status product-amount-increase-response)))
      (t/is (= (str "Session " (:session-id admin-promotion) " is not active")
               (:body product-amount-increase-response))))))

(t/deftest existing-product-amount-decrease
  (t/testing "An admin user is able to decrease the available amount of an existing product"
    (let [user-a "alice"
          product-name "carrot"
          amount-to-decrease 1
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          product-amount-decrease-response (decrease-product-amount product-name amount-to-decrease (:session-id admin-promotion))]
      (t/is (= 200 (:status product-amount-decrease-response)))
      (t/is (= (str "Product " product-name " amount has been decreased by " amount-to-decrease)
               (:body product-amount-decrease-response))))))

(t/deftest nonexistent-product-amount-decrease
  (t/testing "An admin user is unable to decrease the available amount of a nonexistent product"
    (let [user-a "alice"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          product-amount-decrease-response (decrease-product-amount product-name 2 (:session-id admin-promotion))]
      (t/is (= 404 (:status product-amount-decrease-response)))
      (t/is (= (str "Product " product-name " does not exist")
               (:body product-amount-decrease-response))))))

(t/deftest non-admin-product-amount-decrease
  (t/testing "A non-admin user is unable to decrease the available amount of an existing product"
    (let [user-a "alice"
          user-b "bob"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _user-creation-response (create-user user-b)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          product-amount-decrease-response (decrease-product-amount product-name 2 user-b-session-id)]
      (t/is (= 403 (:status product-amount-decrease-response)))
      (t/is (= (str "Session " user-b-session-id " does not belong to an admin user")
               (:body product-amount-decrease-response))))))

(t/deftest expired-admin-session-product-amount-decrease
  (t/testing "An expired admin session is unable to decrease the available amount of an existing product"
    (let [user-a "alice"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          _user-a-logout-response (logout-user (:session-id admin-promotion))
          product-amount-decrease-response (decrease-product-amount product-name 2 (:session-id admin-promotion))]
      (t/is (= 401 (:status product-amount-decrease-response)))
      (t/is (= (str "Session " (:session-id admin-promotion) " is not active")
               (:body product-amount-decrease-response))))))

(t/deftest existing-product-cart-add
  (t/testing "A user is able to add an existing product to their cart"
    (let [user-a "alice"
          user-b "bob"
          product-name "carrot"
          amount-to-add-to-cart 2
          admin-promotion (create-first-admin-user user-a)
          product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          product-id (get-product-id product-creation-response)
          user-creation-response (create-user user-b)
          user-b-id (get-user-id user-creation-response)
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          product-cart-add-response (add-product-to-cart product-name amount-to-add-to-cart user-b-session-id)]
      (t/is (= 200 (:status product-cart-add-response)))
      (t/is (= (str "An amount of "
                    amount-to-add-to-cart
                    " of product "
                    product-id
                    " has been added to cart of user "
                    user-b-id)
               (:body product-cart-add-response))))))

(t/deftest nonexistent-product-cart-add
  (t/testing "A user is not able to add a nonexistent product to their cart"
    (let [user-a "alice"
          product-name "carrot"
          _user-creation-response (create-user user-a)
          user-login-response (login-user user-a)
          session-id (get-session-id user-login-response)
          product-cart-add-response (add-product-to-cart product-name 2 session-id)]
      (t/is (= 404 (:status product-cart-add-response)))
      (t/is (= (str "Product " product-name " does not exist")
               (:body product-cart-add-response))))))

(t/deftest exceeding-amount-product-cart-add
  (t/testing "A user is not able to add to their cart an amount of a certain product greater than the registered amount in inventory"
    (let [user-a "alice"
          user-b "bob"
          product-name "carrot"
          product-amount 20
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 product-amount (:session-id admin-promotion))
          _user-creation-response (create-user user-b)
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          product-cart-add-response (add-product-to-cart product-name (inc product-amount) user-b-session-id)]
      (t/is (= 409 (:status product-cart-add-response)))
      (t/is (= (str "Product "
                    product-name
                    " available amount is "
                    product-amount
                    " which is lower than requested amount "
                    (inc product-amount)
                    " plus reserved amount 0")
               (:body product-cart-add-response))))))

(t/deftest non-positive-amount-product-cart-add
  (t/testing "A user is not able to add to their cart a non-positive amount of a certain product"
    (let [user-a "alice"
          user-b "bob"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          _user-creation-response (create-user user-b)
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)]
      (t/testing "zero amount"
        (let [zero-amount 0
              product-cart-add-response (add-product-to-cart product-name zero-amount user-b-session-id)]
          (t/is (= 400 (:status product-cart-add-response)))
          (t/is (= (str "Amount must be positive. Amount sent was " zero-amount)
                   (:body product-cart-add-response)))))
      (t/testing "negative amount"
        (let [negative-amount -1
              product-cart-add-response (add-product-to-cart product-name negative-amount user-b-session-id)]
          (t/is (= 400 (:status product-cart-add-response)))
          (t/is (= (str "Amount must be positive. Amount sent was " negative-amount)
                   (:body product-cart-add-response))))))))

(t/deftest expired-session-product-cart-add
  (t/testing "An expired session is not able to add a positive amount of a certain product to the corresponding cart"
    (let [user-a "alice"
          user-b "bob"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          _user-creation-response (create-user user-b)
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          _user-b-logout-response (logout-user user-b-session-id)
          product-cart-add-response (add-product-to-cart product-name 1 user-b-session-id)]
      (t/is (= 401 (:status product-cart-add-response)))
      (t/is (= (str "Session " user-b-session-id " is not active")
               (:body product-cart-add-response))))))

(t/deftest existing-product-cart-removal
  (t/testing "A user is able to remove a product from their cart"
    (let [user-a "alice"
          user-b "bob"
          product-name "carrot"
          cart-adding-amount 2
          partial-removal-amount 1
          admin-promotion (create-first-admin-user user-a)
          product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          product-id (get-product-id product-creation-response)
          user-creation-response (create-user user-b)
          user-id (get-user-id user-creation-response)
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          _product-cart-add-response (add-product-to-cart product-name cart-adding-amount user-b-session-id)]
      (t/testing "partial removal"
        (let [product-cart-removal-response (remove-product-from-cart product-name partial-removal-amount user-b-session-id)]
          (t/is (= 200 (:status product-cart-removal-response)))
          (t/is (= (str "An amount of "
                        partial-removal-amount
                        " of product "
                        product-id
                        " has been removed from cart of user "
                        user-id)
                   (:body product-cart-removal-response)))))
      (t/testing "complete removal"
        (let [product-cart-removal-response (remove-product-from-cart product-name (- cart-adding-amount partial-removal-amount) user-b-session-id)]
          (t/is (= 200 (:status product-cart-removal-response)))
          (t/is (= (str "Product " product-id " has been removed from cart of user " user-id)
                   (:body product-cart-removal-response))))))))

(t/deftest nonexistent-product-cart-removal
  (t/testing "A user is unable to remove a nonexistent product from their cart"
    (let [user-a "alice"
          product-name "carrot"
          _user-creation-response (create-user user-a)
          user-a-login-response (login-user user-a)
          user-a-session-id (get-session-id user-a-login-response)
          product-cart-removal-response (remove-product-from-cart product-name 1 user-a-session-id)]
      (t/is (= 404 (:status product-cart-removal-response)))
      (t/is (= (str "Product " product-name " does not exist")
               (:body product-cart-removal-response))))))

(t/deftest non-positive-amount-product-cart-removal
  (t/testing "A user is unable to remove a non-positive amount of a certain product from their cart"
    (let [user-a "alice"
          user-b "bob"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          _user-creation-response (create-user user-b)
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          _product-cart-add-response (add-product-to-cart product-name 2 user-b-session-id)]
      (t/testing "zero amount"
        (let [zero-amount 0
              product-cart-removal-response (remove-product-from-cart product-name zero-amount user-b-session-id)]
          (t/is (= 400 (:status product-cart-removal-response)))
          (t/is (= (str "Amount must be positive. Amount sent was " zero-amount)
                   (:body product-cart-removal-response)))))
      (t/testing "negative amount"
        (let [negative-amount -1
              product-cart-removal-response (remove-product-from-cart product-name negative-amount user-b-session-id)]
          (t/is (= 400 (:status product-cart-removal-response)))
          (t/is (= (str "Amount must be positive. Amount sent was " negative-amount)
                   (:body product-cart-removal-response))))))))

(t/deftest expired-session-product-cart-removal
  (t/testing "An expired session is unable to remove a product from the corresponding cart"
    (let [user-a "alice"
          user-b "bob"
          product-name "carrot"
          admin-promotion (create-first-admin-user user-a)
          _product-creation-response (register-product product-name 3.99 20 (:session-id admin-promotion))
          _user-creation-response (create-user user-b)
          user-b-login-response (login-user user-b)
          user-b-session-id (get-session-id user-b-login-response)
          _product-cart-add-response (add-product-to-cart product-name 2 user-b-session-id)
          _user-b-logout-response (logout-user user-b-session-id)
          product-cart-removal-response (remove-product-from-cart product-name 1 user-b-session-id)]
      (t/is (= 401 (:status product-cart-removal-response)))
      (t/is (= (str "Session " user-b-session-id " is not active")
               (:body product-cart-removal-response))))))
