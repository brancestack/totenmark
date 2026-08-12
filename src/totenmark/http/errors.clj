(ns totenmark.http.errors
  (:require [io.pedestal.interceptor.chain :as chain]
            [totenmark.http.response :as response]))

(defn- malformed-request?
  [exception]
  (contains? #{:io.pedestal.http.body-params/body-params
               :io.pedestal.http.route/query-params
               :io.pedestal.http.route/path-params-decoder}
             (:interceptor (ex-data exception))))

(def interceptor
  {:name ::errors
   :error
   (fn [context exception]
     (if (malformed-request? exception)
       (-> context
           chain/clear-error
           (assoc :response
                  (response/json-response
                   400
                   {:msg "Não foi possível entender a requisição."})))
       (do
         (response/log-error "unhandled-request" exception)
         (-> context
             chain/clear-error
             (assoc :response
                    (response/json-response
                     500
                     {:msg "Ocorreu um erro inesperado."}))))))})
