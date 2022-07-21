(ns app.handlers.inventory
  (:require [integrant.core :as ig]
            [honey.sql :as sql]))

(defn product-not-found-response [name]
  {:status 404
   :body (str "Product " name " does not exist")})

(defn product-exists [querier name]
  (let [query {:select [[[:count :*]]]
               :from [:inventory]
               :where [:= :name name]}
        result (querier (sql/format query))]
    (-> result
        (nth 0)
        :count
        (= 1))))

(defn register-new-product [transactor name price amount]
  (let [insertion {:insert-into :inventory
                   :columns [:name :price :amount]
                   :values [[name price amount]]}]
    (transactor (sql/format insertion))
    {:status 201
     :body (str "Product " name " registered into inventory")}))

(defn delete-product [transactor name]
  (let [deletion {:delete-from :inventory
                  :where [:= :name name]}]
    (transactor (sql/format deletion))
    {:status 200
     :body (str "Product " name " removed from inventory")}))

(defn increase-product-amount [transactor name amount-to-increase]
  (let [update {:update :inventory
                :set {:amount [:+ :amount amount-to-increase]}
                :where [:= :name name]}]
    (transactor (sql/format update))
    {:status 200
     :body (str "Product " name " amount has been increased by " amount-to-increase)}))

(defn attempt-to-register-product [transactor querier name price amount]
  (if (not (product-exists querier name))
    (register-new-product transactor name price amount)
    {:status 409
     :body (str "Product " name " is already registered")}))

(defmethod ig/init-key ::post-handler
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          price (:price body-params)
          amount (:amount body-params)]
      (attempt-to-register-product transactor querier name price amount))))

(defn attempt-to-delete-product [transactor querier name]
  (if (product-exists querier name)
    (delete-product transactor name)
    (product-not-found-response name)))

(defmethod ig/init-key ::delete-handler
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)]
      (attempt-to-delete-product transactor querier name))))

(defn attempt-to-increase-product-amount [transactor querier name amount-to-increase]
  (if (product-exists querier name)
    (increase-product-amount transactor name amount-to-increase)
    (product-not-found-response name)))

(defmethod ig/init-key ::amount-increase-handler
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          amount-to-increase (:amountToIncrease body-params)]
      (attempt-to-increase-product-amount transactor querier name amount-to-increase))))
