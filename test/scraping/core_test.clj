(ns scraping.core-test
	(:require
		[midje.sweet :refer :all]
		[scraping.core :refer :all]
		[clojure.string :as str]
		[clj.helpers :as helpers]
		[cheshire.core :as json]
		[net.cgrand.enlive-html :as html]
		[clj-http.client :as client]
		[clj-http.fake :refer [with-fake-routes]]
		[clojure.java.io :as io]))
