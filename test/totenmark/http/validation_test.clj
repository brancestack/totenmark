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
                     {:username "" :email "bad" :password "short"}))))))

(deftest validates-product-input
  (is (empty? (validation/product-create-errors
               {:product-name "Bike"
                :description "Used"
                :price 100
                :category "sale"
                :status "available"})))
  (is (seq (validation/product-create-errors
            {:product-name "Bike"
             :price -1
             :category "unknown"
             :status "gone"}))))
