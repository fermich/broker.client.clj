(ns pl.fermich.bosclient.broker-client-test
  (:use [clojure.test]
        [clojure.string :only (join)])
  (:gen-class)
  (:import [java.text SimpleDateFormat]
           [java.util Date]))

(use 'pl.fermich.bosclient.broker-client)

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))
