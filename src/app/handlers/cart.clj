(ns app.handlers.cart
  (:require [app.utils :as utils]
            [integrant.core :as ig]
            [honey.sql :as sql])
  (:import [java.util UUID]))

(defn add-product-to-cart [transactor user-id product-id amount]
  (let [upsert {:insert-into :cart-entries
                :columns [:user-id :product-id :amount]
                :values [[user-id product-id amount]]
                :on-conflict {:on-constraint :unique-cart-entries}
                :do-update-set {:amount [:+ :cart-entries.amount amount]}}]
    (transactor (sql/format upsert))
    {:status 200
     :body (str "An amount of " amount " of product " product-id " has been added to cart of user " user-id)}))

(defn clean-cart [transactor user-id]
  (let [deletion {:delete-from :cart-entries
                  :where [:= :user-id user-id]}]
    (transactor (sql/format deletion))
    {:status 200
     :body (str "Cart of user " user-id " has been cleaned")}))

(defn reserved-product-amount [querier user-id product-id]
  (let [query {:select [:amount]
               :from [:cart-entries]
               :where [:and [:= :user-id user-id]
                            [:= :product-id product-id]]}
        result (querier (sql/format query))]
    (-> result
        (nth 0)
        :amount)))

(defn attempt-to-add-product-to-cart [transactor querier user-id product-name requested-amount]
  (let [product-id (utils/product-exists querier product-name)]
    (if product-id
      (let [available-product-amount (utils/current-product-amount querier product-name)
            reserved-product-amount (reserved-product-amount querier user-id product-id)]
        (cond
          (not (pos? requested-amount))
          (utils/non-positive-amount-response requested-amount)
          (not (<= (+ requested-amount reserved-product-amount) available-product-amount))
          {:status 409
           :body (str "Product "
                      product-name
                      " available amount is "
                      available-product-amount
                      " which is lower than requested amount "
                      requested-amount
                      " plus reserved amount "
                      reserved-product-amount)}
          :else
          (add-product-to-cart transactor user-id product-id requested-amount)))
      (utils/product-not-found-response product-name))))

(defmethod ig/init-key ::add
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          product-name (:product body-params)
          amount (:amount body-params)
          session-id (UUID/fromString (:session body-params))
          user-id (utils/session-id->user-id querier session-id)]
      (attempt-to-add-product-to-cart transactor querier user-id product-name amount))))

(defmethod ig/init-key ::clean
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          session-id (UUID/fromString (:session body-params))
          user-id (utils/session-id->user-id querier session-id)]
      (clean-cart transactor user-id))))
