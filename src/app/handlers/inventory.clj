(ns app.handlers.inventory
  (:require [app.utils :as utils]
            [integrant.core :as ig]
            [honey.sql :as sql]))

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

(defn decrease-product-amount [transactor name amount-to-decrease]
  (let [update {:update :inventory
                :set {:amount [:- :amount amount-to-decrease]}
                :where [:= :name name]}]
    (transactor (sql/format update))
    {:status 200
     :body (str "Product " name " amount has been decreased by " amount-to-decrease)}))

(defn change-existing-product-price [transactor name new-price]
  (let [update {:update :inventory
                :set {:price new-price}
                :where [:= :name name]}]
    (transactor (sql/format update))
    {:status 200
     :body (str "Product " name " had its price updated to " new-price)}))

(defn attempt-to-register-product [transactor querier name price amount]
  (if (not (utils/product-exists querier name))
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
  (if (utils/product-exists querier name)
    (delete-product transactor name)
    (utils/product-not-found-response name)))

(defmethod ig/init-key ::delete-handler
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)]
      (attempt-to-delete-product transactor querier name))))

(defn attempt-to-increase-product-amount [transactor querier name amount-to-increase]
  (if (utils/product-exists querier name)
    (increase-product-amount transactor name amount-to-increase)
    (utils/product-not-found-response name)))

(defmethod ig/init-key ::amount-increase-handler
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          amount-to-increase (:amountToIncrease body-params)]
      (attempt-to-increase-product-amount transactor querier name amount-to-increase))))

(defn attempt-to-decrease-existing-product-amount [transactor querier name amount-to-decrease]
  (let [current-product-amount-query {:select [:amount]
                                      :from [:inventory]
                                      :where [:= :name name]}
        current-product-amount-result (querier (sql/format current-product-amount-query))
        current-product-amount (-> current-product-amount-result
                                   (nth 0)
                                   :amount)]
    (if (<= amount-to-decrease current-product-amount)
      (decrease-product-amount transactor name amount-to-decrease)
      {:status 400
       :body (str "Product " name " current amount is " current-product-amount " which is lower than " amount-to-decrease)})))

(defn attempt-to-decrease-product-amount [transactor querier name amount-to-decrease]
  (if (utils/product-exists querier name)
    (attempt-to-decrease-existing-product-amount transactor querier name amount-to-decrease)
    (utils/product-not-found-response name)))

(defmethod ig/init-key ::amount-decrease-handler
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          amount-to-decrease (:amountToDecrease body-params)]
      (attempt-to-decrease-product-amount transactor querier name amount-to-decrease))))

(defn attempt-to-change-existing-product-price [transactor name new-price]
  (if (<= 0 new-price)
    (change-existing-product-price transactor name new-price)
    {:status 400
     :body (str "Price must be non-negative. Price sent was " new-price)}))

(defn attempt-to-change-product-price [transactor querier name new-price]
  (if (utils/product-exists querier name)
    (attempt-to-change-existing-product-price transactor name new-price)
    (utils/product-not-found-response name)))

(defmethod ig/init-key ::price-change-handler
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          new-price (:price body-params)]
      (attempt-to-change-product-price transactor querier name new-price))))
