(ns totenmark.http.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [totenmark.http.validation :as validation]))

(deftest validates-user-input
  (testing "valid creation data"
    (is (empty? (validation/user-create-errors
                 {:username "alice"
                  :email "alice@example.com"
                  :password "password123"}))))
  (testing "invalid creation data"
    (is (= 3 (count (validation/user-create-errors
                     {:username "" :email "bad" :password "short"})))))
  (testing "unsupported fields and BCrypt byte length"
    (is (seq (validation/user-create-errors
              {:username "alice"
               :email "alice@example.com"
               :password "password123"
               :role "admin"})))
    (is (empty? (validation/user-create-errors
                 {:username "alice"
                  :email "alice@example.com"
                  :password (apply str (repeat 72 "a"))})))
    (is (seq (validation/user-update-errors
              {:id 1
               :password (apply str (repeat 73 "a"))})))
    (is (seq (validation/user-update-errors {:id 0 :username "alice"})))
    (is (seq (validation/user-update-errors {:id 1})))))

(deftest validates-product-input
  (is (empty? (validation/product-create-errors
               {:product-name "Bike"
                :description "Used"
                :price 100
                :category "sale"
                :status "available"
                :image-urls ["https://example.com/bike.jpg"]})))
  (is (seq (validation/product-create-errors
            {:product-name "Bike"
             :price -1
             :category "unknown"
             :status "gone"})))
  (is (empty? (validation/product-create-errors
               {:product-name "Maximum price"
                :description nil
                :price Long/MAX_VALUE
                :category "sale"
                :status "available"})))
  (is (seq (validation/product-create-errors
            {:product-name "Too large"
             :description {:not "text"}
             :price (inc (bigint Long/MAX_VALUE))
             :category "sale"
             :status "available"}))))

(deftest validates-product-images
  (is (seq (validation/product-create-errors
            {:product-name "Bike"
             :price 100
             :category "sale"
             :status "available"
             :image-urls ["file:///secret.jpg"]})))
  (is (seq (validation/product-update-errors
            {:product-id 1
             :image-urls (vec (repeat 9 "https://example.com/image.jpg"))})))
  (is (empty? (validation/product-update-errors
               {:product-id 1
                :image-urls ["HTTPS://example.com/image.jpg"]})))
  (is (seq (validation/product-update-errors
            {:product-id 1
             :description 42})))
  (is (seq (validation/product-update-errors
            {:product-id -1
             :price 10}))))
