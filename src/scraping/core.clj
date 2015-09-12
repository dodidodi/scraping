(ns scraping.core
	(:require 
		[clojure.string :as str]
		[clj.helpers :as helpers]
		[cheshire.core :as json]
		[net.cgrand.enlive-html :as html]
		[clj-http.client :as client]
		[clojure.java.io :as io]))
