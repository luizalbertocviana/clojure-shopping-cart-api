(ns app.handlers.inventory
  (:require [integrant.core :as ig]
            [honey.sql :as sql]))

(defn register-new-product [transactor name price amount]
  (let [insertion {:insert-into :inventory
                   :columns [:name :price :amount]
                   :values [[name price amount]]}]
    (transactor (sql/format insertion))
    {:status 201
     :body (str "Product " name " registered into inventory")}))

(defn attempt-to-register-product [transactor querier name price amount]
  (let [product-exists-query {:select [[[:count :*]]]
                              :from [:inventory]
                              :where [:= :name name]}
        product-exists-result (querier (sql/format product-exists-query))
        product-exists (-> product-exists-result
                           (nth 0)
                           :count
                           (= 1))]
    (if (not product-exists)
      (register-new-product transactor name price amount)
      {:status 409
       :body (str "Product " name " is already registered")})))

(defmethod ig/init-key ::post-handler
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          price (:price body-params)
          amount (:amount body-params)]
      (attempt-to-register-product transactor querier name price amount))))
