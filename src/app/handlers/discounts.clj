(ns app.handlers.discounts
  (:require [app.utils :as utils]
            [integrant.core :as ig]
            [honey.sql :as sql])
  (:import [java.util UUID]))

(defn discount-coupon-exists [querier coupon-name]
  (let [query {:select [:id]
               :from [:discounts]
               :where [:= :name coupon-name]}
        result (querier (sql/format query))]
    (if (= 1 (count result))
      (-> result
          (nth 0)
          :id)
      nil)))

(defn create-new-discount-coupon [transactor name amount discount]
  (let [insertion {:insert-into :discounts
                   :columns [:name :amount :discount]
                   :values [[name amount discount]]}]
    (transactor (sql/format insertion))
    {:status 201
     :body (str "Discount coupon " name " created")}))

(defn apply-valid-discount-coupon [transactor coupon-id user-id]
  (let [user-applied-coupon-insertion {:insert-into :applied-discounts
                                       :columns [:user-id :discount-id]
                                       :values [[user-id coupon-id]]}
        coupon-amount-update {:update :discounts
                              :set {:amount [:- :amount 1]}
                              :where [:= :id coupon-id]}]
    (transactor (sql/format user-applied-coupon-insertion))
    (transactor (sql/format coupon-amount-update))
    {:status 200
     :body (str "Discount coupon " coupon-id " applied to user " user-id)}))

(defn attempt-to-create-new-discount-coupon [transactor name amount discount]
  (cond
    (not (pos? amount))
    (utils/non-positive-amount-response amount)
    (not (<= 0.01 discount 1.00))
    {:status 400
     :body (str "Discount must be between 0.01 and 1.00. Discount sent was " discount)}
    :else
    (create-new-discount-coupon transactor name amount discount)))

(defn attempt-to-create-discount-coupon [transactor querier name amount discount]
  (if (not (discount-coupon-exists querier name))
    (attempt-to-create-new-discount-coupon transactor name amount discount)
    {:status 409
     :body (str "Discount coupon " name " is already registered")}))

(defmethod ig/init-key ::post
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          amount (:amount body-params)
          discount (:discount body-params)]
      (attempt-to-create-discount-coupon transactor querier name amount discount))))

(defn attempt-to-apply-valid-discount-coupon [transactor querier coupon-id user-id]
  (let [user-has-no-applied-coupon-query {:select [[[:count :*]]]
                                          :from [:applied-discounts]
                                          :where [:= :user-id user-id]}
        user-has-no-applied-coupon-result (querier (sql/format user-has-no-applied-coupon-query))
        user-has-no-applied-coupon (-> user-has-no-applied-coupon-result
                                       (nth 0)
                                       :count
                                       (= 0))]
    (if user-has-no-applied-coupon
      (apply-valid-discount-coupon transactor coupon-id user-id)
      {:status 409
       :body (str "User id " user-id " has already an applied coupon")})))

(defn attempt-to-apply-existing-discount-coupon [transactor querier coupon-id user-id]
  (let [coupon-is-valid-query {:select [[[:count :*]]]
                               :from [:discounts]
                               :where [:and [:= :id coupon-id]
                                            [:<= [:now] :expires-on]
                                            [:<= 1 :amount]]}
        coupon-is-valid-result (querier (sql/format coupon-is-valid-query))
        coupon-is-valid (-> coupon-is-valid-result
                            (nth 0)
                            :count
                            (= 1))]
    (if coupon-is-valid
      (attempt-to-apply-valid-discount-coupon transactor querier coupon-id user-id)
      {:status 409
       :body (str "Discount coupon " coupon-id " has expired")})))

(defn attempt-to-apply-discount-coupon [transactor querier coupon-name user-id]
  (let [coupon-id (discount-coupon-exists querier coupon-name)]
    (if coupon-id
      (attempt-to-apply-existing-discount-coupon transactor querier coupon-id user-id)
      {:status 404
       :body (str "Discount coupon " coupon-name " does not exist")})))

(defmethod ig/init-key ::put
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          coupon-name (:name body-params)
          session-id (UUID/fromString (:session body-params))
          user-id (utils/session-id->user-id querier session-id)]
      (attempt-to-apply-discount-coupon transactor querier coupon-name user-id))))
