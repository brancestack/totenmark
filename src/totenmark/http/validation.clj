(ns totenmark.http.validation
  (:require [clojure.string :as str]))

(defn- non-blank-string?
  [value]
  (and (string? value) (not (str/blank? value))))

(defn- valid-email?
  [value]
  (and (non-blank-string? value)
       (boolean (re-matches #"^[^\s@]+@[^\s@]+\.[^\s@]+$" value))))

(defn user-create-errors
  [{:keys [username email password]}]
  (cond-> []
    (not (non-blank-string? username)) (conj "username is required")
    (not (valid-email? email)) (conj "email must be valid")
    (not (and (string? password) (<= 8 (count password))))
    (conj "password must contain at least 8 characters")))

(defn user-update-errors
  [attrs]
  (let [allowed #{:id :username :email :password}
        changed (dissoc attrs :id)]
    (cond-> []
      (not (integer? (:id attrs))) (conj "id must be an integer")
      (empty? changed) (conj "at least one field must be provided")
      (seq (remove allowed (keys attrs))) (conj "request contains unsupported fields")
      (and (contains? attrs :username) (not (non-blank-string? (:username attrs))))
      (conj "username cannot be blank")
      (and (contains? attrs :email) (not (valid-email? (:email attrs))))
      (conj "email must be valid")
      (and (contains? attrs :password)
           (not (and (string? (:password attrs))
                     (<= 8 (count (:password attrs))))))
      (conj "password must contain at least 8 characters"))))

(defn product-create-errors
  [{:keys [product-name price category status] :as attrs}]
  (cond-> []
    (not (non-blank-string? product-name)) (conj "product-name is required")
    (not (and (integer? price) (not (neg? price)))) (conj "price must be a non-negative integer")
    (not (#{"donation" "sale"} category)) (conj "category must be donation or sale")
    (not (#{"available" "reserved"} status)) (conj "status must be available or reserved")
    (seq (remove #{:product-name :description :price :category :status} (keys attrs)))
    (conj "request contains unsupported fields")))

(defn product-update-errors
  [attrs]
  (let [allowed #{:product-id :product-name :description :price :category :status}
        changed (dissoc attrs :product-id)]
    (cond-> []
      (not (integer? (:product-id attrs))) (conj "product-id must be an integer")
      (empty? changed) (conj "at least one field must be provided")
      (seq (remove allowed (keys attrs))) (conj "request contains unsupported fields")
      (and (contains? attrs :product-name)
           (not (non-blank-string? (:product-name attrs))))
      (conj "product-name cannot be blank")
      (and (contains? attrs :price)
           (not (and (integer? (:price attrs)) (not (neg? (:price attrs))))))
      (conj "price must be a non-negative integer")
      (and (contains? attrs :category)
           (not (#{"donation" "sale"} (:category attrs))))
      (conj "category must be donation or sale")
      (and (contains? attrs :status)
           (not (#{"available" "reserved"} (:status attrs))))
      (conj "status must be available or reserved"))))
