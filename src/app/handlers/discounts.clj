(ns app.handlers.discounts
  (:require [integrant.core :as ig]
            [honey.sql :as sql]))

(defn create-new-discount-coupon [transactor name amount discount]
  (let [insertion {:insert-into :discounts
                   :columns [:name :amount :discount]
                   :values [[name amount discount]]}]
    (transactor (sql/format insertion))
    {:status 201
     :body (str "Discount coupon " name "created")}))

(defn attempt-to-create-new-discount-coupon [transactor name amount discount]
  (cond
    (not (pos? amount))
    {:status 400
     :body (str "Amount must be positive. Amount sent was " amount)}
    (not (<= 0.01 discount 1.00))
    {:status 400
     :body (str "Discount must be between 0.01 and 1.00. Discount sent was " discount)}
    :else
    (create-new-discount-coupon transactor name amount discount)))

(defn attempt-to-create-discount-coupon [transactor querier name amount discount]
  (let [discount-coupon-exists-query {:select [[[:count :*]]]
                                      :from [:discounts]
                                      :where [:= :name name]}
        discount-coupon-exists-result (querier (sql/format discount-coupon-exists-query))
        discount-coupon-exists (-> discount-coupon-exists-result
                                   (nth 0)
                                   :count
                                   (= 1))]
    (if (not discount-coupon-exists)
      (attempt-to-create-new-discount-coupon transactor name amount discount)
      {:status 409
       :body (str "Discount coupon " name " is already registered")})))

(defmethod ig/init-key ::post
  [_ {:keys [transactor querier]}]
  (fn [req]
    (let [body-params (:body-params req)
          name (:name body-params)
          amount (:amount body-params)
          discount (:discount body-params)]
      (attempt-to-create-discount-coupon transactor querier name amount discount))))
