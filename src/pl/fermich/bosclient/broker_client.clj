(ns pl.fermich.bosclient.broker-client
  (:import [org.jonnakkerud.utils SHA1]
           [java.io BufferedReader StringReader ByteArrayInputStream]
           [java.text SimpleDateFormat]
           [java.util Date])
  (:require [clojure.xml :as xml])
  (:use [clojure.java.io]
        [clojure.string :only (trim join)]
        [pl.fermich.bosclient.http-client])
  (:gen-class))


(def challengeUrl "https://www.bossa.pl:443/")
(def loginUrl "https://www.bossa.pl:443/bossa/login")
(def logoutUrl "https://www.bossa.pl:443/bossa/logout")
(def secListUrl "https://www.bossa.pl/bossa/ordernew?RSecCode=PLPGNIG00014")
(def secInfoUrl "https://www.bossa.pl/bossa/secinfo?")
(def commissionNewUrl "https://www.bossa.pl/bossa/commissionnew?")
(def orderNewUrl "https://www.bossa.pl/bossa/ordernew?")


(defrecord Security [sym code secid])

(defn- create-security-record
  [secStr]
  (let [secInfo (re-seq #"\'[^\'\,)]+" secStr)]
    (Security.
      (trim (apply str (rest (first secInfo))))
      (trim (apply str (rest (second secInfo))))
      (trim (apply str (rest (last secInfo)))))))

(defn- read-sec-list
  [securitiesDoc]
  (first (filter #(not (nil? %))
           (for [line (line-seq (BufferedReader. (StringReader. securitiesDoc)))]
             (if-let [secList (re-seq #"new sec\([^\)]+" line)]
               (map create-security-record secList))))))

(defn- get-lgnChallengeHex
  [httpClient challengeUrl]
  (let [response (do-get-request httpClient challengeUrl)]
    (last (re-find #"LgnChallengeHex\" value=\"([^\"]+)\"" response))))

(defn read-reqNo
  [document]
  (last (re-find #"name=\"ReqNo\" value=\"([^\"]+)" document)))

(defn get-reqNo
  [httpClient challengeUrl]
  (read-reqNo (do-get-request httpClient secListUrl)))


(defn mock-commision-new[]
  (slurp "resources/OrderValueData.xml"))

(defn mock-sec-order[]
  (slurp "resources/SecOrderData.xml"))

(defn mock-reqNo[]
  "66666666")

(defn calculate-SecSubAccSCZC
  [secmeanprice secbalance ordQty ordNetValue]
  (/ (Math/round (/ (* 100.0 (+ (* secmeanprice secbalance) ordNetValue)) (+ secbalance ordQty))) 100.0))

(defprotocol BrokerOps
  (login [this login password])
  (logout [this])
  (get-sec-list [this])
  (get-sec-info [this secId])
  (commision-new [this secId ordQty ordLimit ordSide])
  (order-new [this secId ordQty ordLimit ordSide ordDate]))

(deftype BrokerClient [httpClient]
  BrokerOps
  (login [this login password]
    (let [lgnChallengeHex (get-lgnChallengeHex httpClient challengeUrl)
          lgnUsrHMACPIN (SHA1/hex_hmac_sha1 (SHA1/hex2str lgnChallengeHex) (SHA1/str_sha1 (str password login)))]
      (do-post-request httpClient loginUrl {"LgnUsrNIK" login "LgnUsrPIN" "" "LgnVASCO" "" "ac" "Zaloguj"
                                            "LgnUsrHMACPIN" lgnUsrHMACPIN "LgnChallengeHex" lgnChallengeHex})))

  (logout [this]
    (do-get-request httpClient logoutUrl))

  (get-sec-list [this]
    (read-sec-list (do-get-request httpClient secListUrl)))


  (get-sec-info [this secId]
    (let [ordDate (.format (new SimpleDateFormat "dd.MM.yyyy") (new Date))
          secInfo (join "&" (map #(join "=" %) {"SecID" secId "OrdDateFrom" ordDate "OrdType" "0" "AdvSecInfo" "A" }))
          secInfoQuery (str secInfoUrl secInfo)
          secInfoDoc (do-get-request httpClient secInfoQuery)]
      (xml/parse (ByteArrayInputStream. (.getBytes secInfoDoc)))))


  (commision-new [this secId ordQty ordLimit ordSide]
    (let [ordDate (.format (new SimpleDateFormat "dd.MM.yyyy") (new Date))
          commNewParams (join "&" (map #(join "=" %) {"SecID" secId "MrkID" "0" "OrdQty" ordQty "OrdLimit" ordLimit "OrdSide" ordSide
                                                      "OrdLimitType" "L" "OrdDateFrom" ordDate "OrdDateTo" ordDate "OrdDateType" "S"
                                                      "OrdDisQty" "" "OrdMinQty" "" "OrdActLimit" "" "OrdType" "0"}))
          commNewQuery (str commissionNewUrl commNewParams)
          commNewDoc (do-get-request httpClient commNewQuery)]
      (xml/parse (ByteArrayInputStream. (.getBytes commNewDoc)))))


  (order-new [this secId ordQty ordLimit ordSide ordDate]
    (let [orderValue (:attrs (xml/parse (ByteArrayInputStream. (.getBytes (mock-commision-new)))))
          secInfo (:attrs (xml/parse (ByteArrayInputStream. (.getBytes (mock-sec-order)))))
          secOrder (join "&" (map #(join "=" %)
                               { "OrdBasket" "N" "ReqID" "0" "AllSec" "true" "OrdSource" "1" "isUTP" "T"
                                 "OrdType" "0" "RFromAdm" "" "OrdID" "0" "FormID" "M1" "Portfolio" ""
                                 "OrdLimitType" "L" "OrdLimitUI" "L" "OrdDateType" "S" "OrdActLimit" ""
                                 "OrdDisQty" "" "OrdMinQty" "" "SecSubAccSCZS" "0.0" "SecSubAccRPT" ""
                                 "SecSubAccRPTV" "" "SecSubAccSCZB" "0.0" "Funds" "666.66"
                                 "OrdSecID" secId "OrdSide" ordSide "OrdQty" ordQty "OrdLimit" ordLimit
                                 "OrdSessionDate" ordDate "ReqNo" (mock-reqNo) "OrdMarketID" (:SecMarketID secInfo)
                                 "OrdQuotation" (:SecQuotation secInfo) "SecSubAcc110" (:SecSubAcc110 secInfo)
                                 "SecSubAcc110A" (+ (Integer/parseInt (:SecSubAcc110 secInfo)) (Integer/parseInt ordQty))
                                 "OrdGrossValue" (:OrdGrossValue orderValue) "OrdCommission" (:OrdCommission orderValue)
                                 "OrdNetValue" (:OrdNetValue orderValue) "SecSubAccSCZC"
                                 (calculate-SecSubAccSCZC
                                   (Double/parseDouble (:SecSubAccSCZ secInfo)) (Double/parseDouble (:SecSubAcc110 secInfo))
                                   (Double/parseDouble ordQty) (Double/parseDouble (:OrdNetValue orderValue))) }))]
      (do-post-request httpClient (str orderNewUrl secOrder) {})))
 )



(defn create-broker-client []
  (->BrokerClient (create-http-client)))
