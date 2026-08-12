(ns totenmark.http.cors
  (:require [totenmark.config :as config]
            [totenmark.http.response :as response]))

(defn- allowed-origin?
  [origin]
  (let [allowed (config/allowed-origins)]
    (or (contains? allowed "*")
        (contains? allowed origin))))

(defn- cors-headers
  [origin]
  {"Access-Control-Allow-Origin" origin
   "Access-Control-Allow-Methods" "GET, POST, PATCH, DELETE, OPTIONS"
   "Access-Control-Allow-Headers" "Authorization, Content-Type, X-Request-ID"
   "Access-Control-Expose-Headers" "X-Request-ID"
   "Access-Control-Max-Age" "600"
   "Vary" "Origin"})

(def interceptor
  {:name ::cors
   :enter
   (fn [context]
     (let [request (:request context)
           origin (get-in request [:headers "origin"])]
       (cond
         (and origin (not (allowed-origin? origin)))
         (assoc context :response
                (response/json-response 403 {:msg "Origem não permitida."}))

         (= :options (:request-method request))
         (assoc context :response
                {:status 204
                 :headers (if origin (cors-headers origin) {})
                 :body ""})

         :else context)))
   :leave
   (fn [context]
     (if-let [origin (get-in context [:request :headers "origin"])]
       (if (allowed-origin? origin)
         (update context :response update :headers merge (cors-headers origin))
         context)
       context))})
