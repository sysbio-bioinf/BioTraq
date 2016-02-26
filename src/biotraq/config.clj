;; Copyright Fabian Schneider and Gunnar Völkel © 2014-2015
;;
;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:
;;
;; The above copyright notice and this permission notice shall be included in
;; all copies or substantial portions of the Software.
;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
;; THE SOFTWARE.

(ns biotraq.config
  (:require
    [clojure.string :as str]
    [clojure.pprint :refer [pprint]])
  (:import
    com.mchange.v2.c3p0.ComboPooledDataSource))



(def ^:private ^:const project-creation-customer-body
"Dear {{customername}},

You recieve this e-mail because you have registered a project at the YOUR LAB.

You can track the progress of your samples through the various steps performed via the following link:
{{trackinglink}}

This e-mail was automatically generated by BioTraq, a sample tracking tool developed by the Research Group for Bioinformatics & Systems Biology, headed by Prof. Dr. Hans A. Kestler.

Yours sincerely,
             YOUR NAME
")


(def ^:private ^:const project-creation-staff-body
"Dear {{staffname}},

Project {{projectnumber}} has been created for customer {% if customername %}{{customername}} ({{customeremail}}){% else %}{{customeremail}}{% endif %}.
You have been registered for notification e-mails about the progess of this project.

You can access the project for modifications via the following link:
{{editlink}}

The tracking view is available via the following link:
{{trackinglink}}

Yours sincerely,
             YOUR NAME
")


(def ^:private ^:const project-progress-customer-body
"Dear {{customername}},

project {{projectnumber}} has been updated.

{{progressinfo}}

You can access additional information using the following link:
{{trackinglink}}

Yours sincerely,
             YOUR NAME
")


(def ^:private ^:const project-progress-staff-body
"Dear {{staffname}},

project {{projectnumber}} for customer {% if customername %}{{customername}} ({{customeremail}}){% else %}{{customeremail}}{% endif %} has been updated.

{{progressinfo}}

You can access the project for modifications via the following link:
{{editlink}}

The tracking view is available via the following link:
{{trackinglink}}

Yours sincerely,
             YOUR NAME
")


(defonce ^:private biotraq-config
  (atom
    {:log-level :info
     :log-file  "biotraq.log"
     :data-base-name "biotraq.db"
     :admin-shutdown? true
     :page-title "Your Core Unit",
     :page-title-link "http://your.core.unit.com",
     :upload-path "uploads/"
     :server-config ^:replace {:port 8000
                               :host "localhost"
                               :join? false
                               :ssl? true
                               :ssl-port 8443
                               :forwarded? false
                               :server-root ""
                               :proxy-url nil
                               :keystore "keystore.jks"
                               :key-password "password" },
     
     :mail-config {:host-config ^:replace {:host "mail.uni-ulm.de"
                                           :user "jsmith"
                                           :pass "secret"
                                           :tls :yes
                                           :port 587}
                   :from "john.smith@uni-ulm.de"
                   :project-creation {:customer
                                      {:subject "{{projectnumber}} Sample Registration at the YOUR LAB",
                                       :body project-creation-customer-body}
                                      :staff
                                      {:subject "Project {{projectnumber}} has been created",
                                       :body project-creation-staff-body}}
                   
                   :project-progress {:customer
                                      {:subject "Progress update of project {{projectnumber}}"
                                       :body project-progress-customer-body}
                                      :staff
                                      {:subject "Progress update of project {{projectnumber}}"
                                       :body project-progress-staff-body}}
                   :cc-notified-staff? false,
                   :send-mail? false}}))


(defn- extract-config-file-content
  [config-map]
  (reduce
    (fn [{:keys [config] :as result-map}, path]
      (let [config-path (list* :mail-config (conj path :body)),
            body (get-in config config-path),
            body-sym (symbol (apply format "%s-%s-message" (map name path)))]
        (-> result-map
          (update-in [:variables] assoc body-sym body)
          (update-in [:config] assoc-in config-path body-sym))))
    {:config config-map}
    [[:project-creation :customer]
     [:project-creation :staff]
     [:project-progress :customer]
     [:project-progress :staff]]))


(defn write-config-file
  [filename, options]
  (let [{:keys [config, variables]} (extract-config-file-content (merge @biotraq-config options))]
    (spit filename      
      (->> variables
        (mapv (fn [[var value]] (format "(def %s\n\"%s\")" var value)))
        (str/join "\n\n")
        (format "%2$s\n\n%1s"
          (with-open [w (java.io.StringWriter.)]
            (pprint config w)
            (str w)))))))


(defn server-config
  []
  (:server-config @biotraq-config))

(defn page-title
  []
  (:page-title @biotraq-config))

(defn page-title-link
  []
  (:page-title-link @biotraq-config))


(defn develop?
  []
  (:develop? @biotraq-config))


(defn admin-shutdown?
  []
  (:admin-shutdown? @biotraq-config))


(defn tracking-server-domain
  []
  (let [{:keys [proxy-url, host, port]} (server-config)]
    (or
      proxy-url
      (cond-> host (not= port 80) (str ":" port)))))


(defn server-root
  []
  (or (:server-root (server-config)) ""))


(defn server-location
  ([path]
    (server-location path, false))
  ([^String path, always-prefix-slash?]
    (let [{:keys [^String server-root]} (server-config)]
      (if (str/blank? server-root)
        path
        (let [slash? (.startsWith path "/")]
          (str
            (when (or slash? always-prefix-slash?) "/")
            server-root
            (when-not (or (str/blank? path) slash?) "/")
            path))))))


(defn trim-slashes
  [^String s]
  (when s
    (let [b (if (.startsWith s "/") 1 0)
          n (.length s)
          e (if (.endsWith s "/") (dec n) n)]
      (.substring s b e))))


(defn normalize-path
  [^String path]
  (cond-> path
    (and (not (.endsWith path "/")) (not (str/blank? path))) (str "/")))



(defn deep-merge
  "Merges the `source` map into the `sink` map such that nested map values are merged as well."
  [sink, source]
  (persistent!
    (reduce-kv
      (fn [res-map, k, source-value]
        (let [sink-value (get sink k)]
          (assoc! res-map k
            (cond
              (-> sink-value meta :replace)
                source-value,              
              (and (map? sink-value) (map? source-value))
                (deep-merge sink-value, source-value)
              :else
                source-value))))
      (transient sink)
      source)))


(defn update-config
  [config]
  (swap! biotraq-config deep-merge
    (-> config
      (update-in [:server-config :server-root] trim-slashes)
      (update-in [:upload-path] normalize-path))))


(defn database-config
  [db-filename]
  {:classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname db-filename})


(def ^:private ^:once db-spec
  (atom (database-config "biotraq.db")))


(defn pool
  [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec)) 
               (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
               (.setUser (:user spec))
               (.setPassword (:password spec))
               ;; expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections (* 30 60))
               ;; expire connections after 3 hours of inactivity:
               (.setMaxIdleTime (* 3 60 60))
               ;; only one connection for SQLite
               (.setMinPoolSize 1)
               (.setMaxPoolSize 1))] 
    {:datasource cpds}))


(def db-connection-pool (atom nil))


(defn db-connection
  []
  (if-let [db-conn @db-connection-pool]
    db-conn
    (swap! db-connection-pool #(if (nil? %) (pool @db-spec) %))))


(defn update-db-name [name]
  (swap! db-spec assoc :subname name)
  (reset! db-connection-pool nil))


(defn upload-path [] (:upload-path @biotraq-config))


(defn mail-config [] (:mail-config @biotraq-config))

(defn send-mail? [] (get-in @biotraq-config [:mail-config :send-mail?]))

(derive ::admin ::user)
(derive ::configadmin ::admin)