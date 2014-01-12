(ns pl.fermich.bosclient.http-client
  (:gen-class)
  (:import [org.apache.http.params BasicHttpParams CoreConnectionPNames HttpProtocolParamBean]
           [org.apache.http.impl.client DefaultHttpClient]
           [org.apache.http.util EntityUtils]
           [org.apache.http.client.methods HttpGet HttpPost]
           [org.apache.http.message BasicNameValuePair]
           [org.apache.http Consts]
           [org.apache.http.client.entity UrlEncodedFormEntity]))


(defn- create-http-params []
  (let [httpParams (new BasicHttpParams)]
      (.setParameter httpParams "Accept" "text/html, application/xhtml+xml, */*")
      (.setParameter httpParams "Accept-Encoding" "gzip, deflate")
      (.setParameter httpParams "Accept-Language" "pl-PL")
      (.setUserAgent (new HttpProtocolParamBean httpParams) "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)")
      httpParams))

(defn- create-request-parameter [col]
  (new BasicNameValuePair (first col) (last col)))

(defn- prepare-post-request
  [actionUrl requestParams]
  (let [httpPost (new HttpPost actionUrl)]
    (.setEntity httpPost (new UrlEncodedFormEntity (map create-request-parameter requestParams) Consts/UTF_8))
    httpPost))

(defn- do-http-request
  [httpClient requestAction]
  (try
    (EntityUtils/toString (.getEntity (.execute httpClient requestAction)))
    (finally (.releaseConnection requestAction))))


(defn create-http-client []
  (let [httpClient (new DefaultHttpClient)]
    (.setParams httpClient (create-http-params))
    httpClient))

(defn do-get-request
  [httpClient actionUrl]
  (do-http-request httpClient (new HttpGet actionUrl)))

(defn do-post-request
  [httpClient actionUrl requestParams]
  (do-http-request httpClient (prepare-post-request actionUrl requestParams)))
