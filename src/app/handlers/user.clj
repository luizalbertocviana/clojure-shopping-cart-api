(ns app.handlers.user
  (:require [integrant.core :as ig]
            [honey.sql :as sql]))

(defn user-exists [querier name]
  (let [query {:select [:id]
               :from [:users]
               :where [:= :name name]}
        query-result (querier (sql/format query))
        name-occurrences (count query-result)]
    (if (= 1 name-occurrences)
      (-> query-result
          (nth 0)
          :id)
      nil)))

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
          user-id (user-exists querier name)]
      (if (not user-id)
        (let [insertion {:insert-into :users
                         :columns [:name]
                         :values [[name]]}]
          (transactor (sql/format insertion))
          {:status 201
           :body (str "User " name " created successfully")})
        {:status 409
         :body (str "User " name " already exists; id " user-id)}))))

(defmethod ig/init-key ::login
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          user-id (user-exists querier name)]
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
