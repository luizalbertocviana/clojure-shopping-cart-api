(ns app.handlers.user
  (:require [integrant.core :as ig]
            [honey.sql :as sql]))

(defmethod ig/init-key ::create
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          query {:select [ [[:count :name]] ]
                 :from [:users]
                 :where [:= :name name]}
          [query-result] (querier (sql/format query))
          name-occurrences (:count query-result)]
      (if (zero? name-occurrences)
        (let [insertion {:insert-into :users
                         :columns [:name]
                         :values [[name]]}]
          (transactor (sql/format insertion))
          {:status 201
           :body (str "User " name " created successfully")})
        {:status 409
         :body (str "User " name " already exists")}))))
