(ns totenmark.http.product-lifecycle-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc :as jdbc]
            [totenmark.http.integration-support
             :refer [*test-ds* create-user-and-token! json-body request
                     with-test-api]]
            [totenmark.utils.jwt :as jwt]))

(use-fixtures :each with-test-api)

(defn- publish!
  [token body]
  (let [response (request :post "/api/products" :token token :body body)]
    (is (= 201 (:status response)))
    (-> response json-body :product)))

(deftest catalog-filters-and-paginates-real-products
  (let [alice-token (create-user-and-token! "catalog-alice")
        bob-token (create-user-and-token! "catalog-bob")
        alice-id (:user-id (jwt/verify alice-token))]
    (publish! alice-token {:product-name "  Mesa de madeira  "
                           :description "Para uma cozinha pequena"
                           :price 250
                           :category "sale"})
    (publish! alice-token {:product-name "Livros"
                           :description "Coleção de ficção científica"
                           :price 0
                           :category "donation"})
    (publish! bob-token {:product-name "Luminária"
                         :description "Luz para mesa de trabalho"
                         :price 70
                         :category "sale"})

    (let [page-one (json-body (request :get "/api/products?limit=2&offset=0"))
          page-two (json-body (request :get "/api/products?limit=2&offset=2"))]
      (is (= 3 (get-in page-one [:pagination :total])))
      (is (true? (get-in page-one [:pagination :has-more])))
      (is (= 2 (count (:items page-one))))
      (is (false? (get-in page-two [:pagination :has-more])))
      (is (= 1 (count (:items page-two))))
      (is (= 3 (count (set (map :product-id
                                (concat (:items page-one)
                                        (:items page-two))))))))

    (is (= 2 (get-in (json-body
                       (request :get "/api/products?category=sale"))
                      [:pagination :total])))
    (is (= 2 (get-in (json-body
                       (request :get (str "/api/products?user-id=" alice-id)))
                      [:pagination :total])))
    (let [search (json-body
                  (request :get "/api/products?q=trabalho"))]
      (is (= 1 (get-in search [:pagination :total])))
      (is (= "Luminária" (get-in search [:items 0 :product-name]))))
    (is (= 1 (get-in (json-body
                       (request :get "/api/products?q=MADEIRA"))
                      [:pagination :total])))
    (is (= 0 (get-in (json-body
                       (request :get "/api/products?q=%25"))
                      [:pagination :total])))))

(deftest only-the-owner-can-edit-and-images-are-replaced-atomically
  (let [owner-token (create-user-and-token! "product-owner")
        stranger-token (create-user-and-token! "product-stranger")
        product (publish! owner-token
                          {:product-name "Camera"
                           :description "Working"
                           :price 300
                           :category "sale"
                           :image-urls ["https://example.com/one.jpg"
                                        "HTTPS://example.com/two.jpg"]})
        product-id (:product-id product)
        path (str "/api/products/" product-id)]
    (is (= ["https://example.com/one.jpg" "HTTPS://example.com/two.jpg"]
           (:images product)))
    (is (= 403 (:status
                (request :patch path
                         :token stranger-token
                         :body {:price 1}))))
    (is (= 422 (:status
                (request :patch path
                         :token owner-token
                         :body {:status "reserved"}))))
    (let [response (request :patch path
                            :token owner-token
                            :body {:product-name "  Câmera revisada "
                                   :description nil
                                   :image-urls []})
          updated (-> response json-body :product)]
      (is (= 200 (:status response)))
      (is (= "Câmera revisada" (:product-name updated)))
      (is (nil? (:description updated)))
      (is (empty? (:images updated))))
    (is (= 0 (-> (jdbc/execute-one!
                  *test-ds*
                  ["SELECT COUNT(*) AS total FROM product_images
                    WHERE product_id = ?" product-id])
                 vals
                 first)))
    (is (= 200 (:status (request :delete path :token owner-token))))
    (is (= 404 (:status (request :get path))))))
