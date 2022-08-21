(ns app.handlers.user
  (:require [app.utils :as utils]
            [integrant.core :as ig]
            [honey.sql :as sql])
  (:import [java.util UUID]))

(defn active-session [querier user-id]
  (let [query {:select [:id]
               :from [:sessions]
               :where [:and [:= :user-id user-id]
                            [:<= [:now] :expires-on]]}
        query-result (querier (sql/format query))]
    (if (= 1 (count query-result))
      (-> query-result
          (nth 0)
          :id)
      nil)))

(defmethod ig/init-key ::create
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          user-id (utils/user-exists querier name)]
      (if (not user-id)
        (let [insertion {:insert-into :users
                         :columns [:name]
                         :values [[name]]}
              result (transactor (sql/format insertion))]
          {:status 201
           :body {:message (str "User " name " created successfully")
                  :id (:id result)}})
        {:status 409
         :body (str "User " name " already exists; id " user-id)}))))

(defmethod ig/init-key ::login
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          user-id (utils/user-exists querier name)]
      (if user-id
        (let [active-session-id (active-session querier user-id)]
          (if (not active-session-id)
            (let [insertion {:insert-into :sessions
                             :columns [:user-id]
                             :values [[user-id]]}
                  _ (transactor (sql/format insertion))
                  active-session-id (active-session querier user-id)]
              {:status 200
               :body {:session active-session-id}})
            {:status 400
             :body (str "user " name " already logged in; session " active-session-id)}))
        {:status 404
         :body (str "User " name " does not exist")}))))

(defn log-active-session-out [transactor session-id]
  (let [update {:update :sessions
                :set {:expires-on [:now]}
                :where [:= :id session-id]}]
    (transactor (sql/format update))
    {:status 200
     :body (str "Session " session-id " finished")}))

(defn log-existing-session-out [transactor querier session-id]
  (if (utils/session-is-active querier session-id)
    (log-active-session-out transactor session-id)
    {:status 400
     :body (str "Session " session-id " is not active")}))

(defn log-user-out [transactor querier session-id]
  (let [session-exists-query {:select [:id]
                              :from [:sessions]
                              :where [:= :id session-id]}
        session-exists-result (querier (sql/format session-exists-query))
        session-exists (= 1 (count session-exists-result))]
    (if session-exists
      (log-existing-session-out transactor querier session-id)
      {:status 404
       :body (str "Session " session-id " does not exist")})))

(defmethod ig/init-key ::logout
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          session-id (UUID/fromString (:session body-params))]
      (log-user-out transactor querier session-id))))
