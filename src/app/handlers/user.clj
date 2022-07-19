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

