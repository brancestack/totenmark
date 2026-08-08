(ns totenmark.http.handlers.products
  (:require [totenmark.db.products :as db.products]
            [totenmark.http.response :as response]
            [totenmark.http.validation :as validation]))

(defn- parse-id
  [value]
  (cond
    (integer? value) value
    (string? value) (parse-long value)
    :else nil))

(defn- query-param
  [request name]
  (or (get-in request [:query-params name])
      (get-in request [:query-params (keyword name)])))

(defn- list-options
  [request]
  (let [limit-value (query-param request "limit")
        offset-value (query-param request "offset")
        limit (if (nil? limit-value) 50 (parse-long limit-value))
        offset (if (nil? offset-value) 0 (parse-long offset-value))
        category (query-param request "category")
        status (query-param request "status")]
    (when (and limit offset
               (<= 1 limit 100)
               (<= 0 offset)
               (or (nil? category) (#{"donation" "sale"} category))
               (or (nil? status) (#{"available" "reserved"} status)))
      {:limit limit
       :offset offset
       :query-text (query-param request "q")
       :category category
       :status status})))

(defn- owner-id
  [product]
  (or (:user-id product) (:user_id product)))

(defn- may-change?
  [request product]
  (= (get-in request [:identity :user-id]) (owner-id product)))

(defn create!
  [request]
  (let [body (:json-params request)
        errors (validation/product-create-errors body)]
    (if (seq errors)
      (response/json-response 422 {:msg "Invalid product data." :errors errors})
      (try
        (db.products/create! (assoc body :user-id (get-in request [:identity :user-id])))
        (response/json-response 201 {:msg "Product created successfully."})
        (catch Exception exception
          (response/failure "create product" exception 400 "Failed to create product."))))))

(defn find-all
  [request]
  (if-let [options (list-options request)]
    (try
      (response/json-response 200 (db.products/find-all options))
      (catch Exception exception
        (response/failure "list products" exception 500 "Failed to retrieve products.")))
    (response/json-response 422
                            {:msg "Invalid filters. Use limit 1-100, a non-negative offset, and supported category/status values."})))

(defn delete!
  [request]
  (let [product-id (parse-id (get-in request [:json-params :product-id]))]
    (if-not product-id
      (response/json-response 422 {:msg "product-id must be an integer."})
      (try
        (if-let [product (db.products/find-by-id product-id)]
          (if (may-change? request product)
            (do
              (db.products/delete! product-id)
              (response/json-response 200 {:msg "Product deleted successfully."}))
            (response/json-response 403 {:msg "You can only delete your own products."}))
          (response/json-response 404 {:msg "Product not found."}))
        (catch Exception exception
          (response/failure "delete product" exception 400 "Failed to delete product."))))))

(defn update!
  [request]
  (let [body (:json-params request)
        product-id (parse-id (:product-id body))
        attrs (assoc body :product-id product-id)
        errors (validation/product-update-errors attrs)]
    (if (seq errors)
      (response/json-response 422 {:msg "Invalid product data." :errors errors})
      (try
        (if-let [product (db.products/find-by-id product-id)]
          (if (may-change? request product)
            (do
              (db.products/update! attrs)
              (response/json-response 200 {:msg "Product updated successfully."}))
            (response/json-response 403 {:msg "You can only update your own products."}))
          (response/json-response 404 {:msg "Product not found."}))
        (catch Exception exception
          (response/failure "update product" exception 400 "Failed to update product."))))))

(defn find-by-id
  [request]
  (if-let [id (parse-id (get-in request [:path-params :id]))]
    (try
      (if-let [product (db.products/find-by-id id)]
        (response/json-response 200 product)
        (response/json-response 404 {:msg "Product not found."}))
      (catch Exception exception
        (response/failure "find product by id" exception 500 "Failed to retrieve product.")))
    (response/json-response 422 {:msg "id must be an integer."})))
