(ns app.handlers.admin
  (:require [app.handlers.utils :as utils]
            [integrant.core :as ig]
            [honey.sql :as sql])
  (:import [java.util UUID]))

(defn promote-to-admin [transactor user-id]
  (let [insertion {:insert-into :admins
                   :columns [:user-id]
                   :values [[user-id]]}]
    (transactor (sql/format insertion))))

(defn success-response [user-name]
  {:status 200
   :body (str "User " user-name " has been promoted to admin")})

(defn attempt-to-promote-first-admin [transactor querier session-id promoting-user user-id]
  (let [promoting-themselves-query {:select [[[:count :*]]]
                                    :from [:sessions :users]
                                    :where [:and [:= :sessions.user-id :users.id]
                                                 [:= :sessions.id session-id]
                                                 [:= :users.id user-id]]}
        promoting-themselves-result (querier (sql/format promoting-themselves-query))
        promoting-themselves (-> promoting-themselves-result
                                 (nth 0)
                                 :count
                                 (= 1))]
    (if promoting-themselves
      (do
        (promote-to-admin transactor user-id)
        (success-response promoting-user))
      {:status 403
       :body (str "Session " session-id " does not belong to user " promoting-user)})))

(defn attempt-to-promote-in-admin-session [transactor querier promoting-user user-id]
  (let [promoting-user-is-admin-query {:select [[[:count :*]]]
                                       :from [:users :admins]
                                       :where [:and [:= :users.id :admins.user-id]
                                                    [:= :users.id user-id]]}
        promoting-user-is-admin-result (querier (sql/format promoting-user-is-admin-query))
        promoting-user-is-admin (-> promoting-user-is-admin-result
                                    (nth 0)
                                    :count
                                    (= 1))]
    (if (not promoting-user-is-admin)
      (do
        (promote-to-admin transactor user-id)
        (success-response promoting-user))
      {:status 409
       :body (str "User " promoting-user " is already an admin")})))

(defn attempt-to-promote-another-admin [transactor querier session-id promoting-user user-id]
  (let [admin-session-query {:select [[[:count :*]]]
                             :from [:sessions :admins]
                             :where [:and [:= :sessions.user-id :admins.user-id]
                                          [:= :sessions.id session-id]]}
        admin-session-result (querier (sql/format admin-session-query))
        admin-session (-> admin-session-result
                          (nth 0)
                          :count
                          (= 1))]
    (if admin-session
      (attempt-to-promote-in-admin-session transactor querier promoting-user user-id)
      {:status 403
       :body (str "Session " session-id " does not belong to an admin user")})))

(defn attempt-to-promote-existing-user [transactor querier session-id promoting-user user-id]
  (let [no-admins-query {:select [[[:count :*]]]
                         :from [:admins]}
        no-admins-result (querier (sql/format no-admins-query))
        no-admins (-> no-admins-result
                      (nth 0)
                      :count
                      (= 0))]
    (if no-admins
      (attempt-to-promote-first-admin transactor querier session-id promoting-user user-id)
      (attempt-to-promote-another-admin transactor querier session-id promoting-user user-id))))

(defn attempt-to-promote-to-admin [transactor querier session-id promoting-user]
  (let [user-id (utils/user-exists querier promoting-user)]
    (if user-id
      (attempt-to-promote-existing-user transactor querier session-id promoting-user user-id)
      {:status 404
       :body (str "User " promoting-user " does not exist")})))

(defmethod ig/init-key ::handler
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          session-id (UUID/fromString (:session body-params))
          promoting-user (:user body-params)]
      (attempt-to-promote-to-admin transactor querier session-id promoting-user))))
