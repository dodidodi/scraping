(defproject scraping "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-fuzzy  "0.3.1"]
                 [cheshire  "5.5.0"]
                 [crouton  "0.1.2"]
                 [enlive  "1.1.6"]
                 [twitter-api  "0.7.8"]
                 [clj-http  "2.0.0" ]
                 [clj-http-fake "1.0.1"]
                 [clj-time  "0.11.0"]
                 ]



  :resource-paths ["resources" "spec/scraping/resources" "test/scraping/resources"]
  :profiles {:dev {:dependencies [[midje "1.7.0"]]}}
  )
