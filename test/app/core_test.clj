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

(defn post-request [route-suffix body]
  (http-client/post (str route-prefix route-suffix)
                    {:content-type :json
                     :body (json/write-str body)
                     :throw-exceptions false}))

(defn create-user [user-name]
  (post-request "/user/create" {:name user-name}))

(defn login-user [user-name]
  (post-request "/user/login" {:name user-name}))

(defn logout-user [session-id]
  (post-request "/user/logout" {:session session-id}))

(defn get-session-id [login-response]
  (-> login-response
      :body
      json/read-str
      (get "session")
      UUID/fromString))

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

(t/deftest a-test
  (t/testing "FIXME, I don't fail anymore."
    (t/is (= 1 1))))
