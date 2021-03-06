(ns api.test.handler
  (:use cheshire.core)
  (:require [clojure.test :refer :all]
            [common.db :refer [conn]]
            [api.handler :refer [app]]
            [api.test.db-tools :refer [setup-ebdb-test-for-conn-fixture
                                       ebdb-test-config]]
            [ring.mock.request :as mock]))

(use-fixtures :once setup-ebdb-test-for-conn-fixture)

(defn =submap
  "Is subm is part of m?"
  [m subm]
  (nil? (second (clojure.data/diff m subm))))

(def api-version "v1")

(defn mock-get-with-auth
  [endpoint params]
  (-> (mock/request :get (str "/" api-version "/" endpoint))
      (mock/query-string params)
      (mock/header "Authorization" "Basic S0pQVzFiR25kRExFV1d1NUxwNUtKQWpndk1LS1NiSkE6c3FYd1RpaFZnVG9YMENkeTY1OW1DVksxZ1B6RjBBMThFR0VwSnRZbEdLQVVOa2dPR09zMnU3dE5UcUk2TGx0Q1VVWWhFdWJjdVQ1SWxQOFF0VFdLT0FLRkVYbTlWYlhWS1lWUmJoeTlTaWk5N3FqS2tsZ2JEa0NZMHY0UXF0Zk4=")
      (mock/content-type "application/json")
      app
      :body
      (parse-string true)))

(defn mock-post-with-auth
  [endpoint params]
  (-> (mock/request :post (str "/" api-version "/" endpoint))
      (mock/body (generate-string params))
      (mock/header "Authorization" "Basic S0pQVzFiR25kRExFV1d1NUxwNUtKQWpndk1LS1NiSkE6c3FYd1RpaFZnVG9YMENkeTY1OW1DVksxZ1B6RjBBMThFR0VwSnRZbEdLQVVOa2dPR09zMnU3dE5UcUk2TGx0Q1VVWWhFdWJjdVQ1SWxQOFF0VFdLT0FLRkVYbTlWYlhWS1lWUmJoeTlTaWk5N3FqS2tsZ2JEa0NZMHY0UXF0Zk4=")
      (mock/content-type "application/json")
      app
      :body
      (parse-string true)))

(defmacro mock-time
  [unix-time & body]
  `(with-redefs [common.util/now-unix (constantly ~unix-time)]
     ~@body))

(deftest base-routes
  (testing "not-found route"
    (let [response (app (mock/request :get "/i-n-v-a-l-i-d"))]
      (is (= 404
             (:status response)))))

  (testing "ok route (for server monitoring)"
    (let [response (app (mock/request :get "/ok"))]
      (is (:success (parse-string (:body response) true)))))
  
  (testing "root route redirects to docs page"
    (let [response (app (mock/request :get "/"))]
      (is (= 302
             (:status response))))))

(deftest availability-routes
  (testing "availability"
    (mock-time
     1472682311 ;; 8/31/2016, 3:25:11 PM Pacific
     (is (= {:success true,
             :availability
             {:time_limit_choices
              [{:fee 599, :text "within 1 hour ($5.99)", :time 60}
               {:fee 399, :text "within 3 hours ($3.99)", :time 180}
               {:fee 299, :text "within 5 hours ($2.99)", :time 300}],
              :gallon_choices ["fillup" 7.5 10 15],
              :octane "87",
              :gas_price 312,
              :tire_pressure_fillup_price 700}}
            (mock-get-with-auth "availability"
                                {:lat "33.995632"
                                 :lng "-118.474990"
                                 :vehicle_id "KmHDbvxQYKjcCX7EPJzM"})))))

  (testing "availability with missing lng"
    (mock-time
     1472682311 ;; 8/31/2016, 3:25:11 PM Pacific
     (is (=submap (mock-get-with-auth "availability"
                                      {:lat "33.995632"
                                       :vehicle_id "KmHDbvxQYKjcCX7EPJzM"})
                  {:success false
                   :code "invalid-input"}))))

  (testing "availability with invalid lng"
    (mock-time
     1472682311 ;; 8/31/2016, 3:25:11 PM Pacific
     (is (=submap (mock-get-with-auth "availability"
                                      {:lat "33.995632"
                                       :lng "blahblahblah"
                                       :vehicle_id "KmHDbvxQYKjcCX7EPJzM"})
                  {:success false
                   :code "invalid-input"}))))

  (testing "availability outside service hours"
    (mock-time
     1472712952 ;; 8/31/2016, 11:55:52 PM Pacific
     (is (=submap (mock-get-with-auth "availability"
                                      {:lat "33.995632"
                                       :lng "-118.474990"
                                       :vehicle_id "KmHDbvxQYKjcCX7EPJzM"})
                  {:success false
                   :code "outside-service-hours"}))))
  
  (testing "availability outside service area"
    (mock-time
     1472682311 ;; 8/31/2016, 3:25:11 PM Pacific
     (is (=submap (mock-get-with-auth "availability"
                                      {:lat "34.321267"
                                       :lng "-118.726863"
                                       :vehicle_id "KmHDbvxQYKjcCX7EPJzM"})
                  {:success false
                   :code "outside-service-area"}))))

  (testing "availability with unknown vehicle ID"
    (mock-time
     1472682311 ;; 8/31/2016, 3:25:11 PM Pacific
     (is (=submap (mock-get-with-auth "availability"
                                      {:lat "33.995632"
                                       :lng "-118.474990"
                                       :vehicle_id "AAAAbvxQYKjcCX7EAAAA"})
                  {:success false
                   :code "invalid-vehicle-id"})))))

(deftest orders-routes
  (testing "getting orders list"
    (is (=submap (mock-get-with-auth "orders/get"
                                     {:limit 3
                                      :start 5
                                      :sort "desc"
                                      :vehicle_id "KmHDbvxQYKjcCX7EPJzM"})
                 {:success true
                  :orders [{:tire_pressure_fillup false
                            :vehicle_id "KmHDbvxQYKjcCX7EPJzM"
                            :total_price 5009
                            :octane "87"
                            :special_instructions ""
                            :status "cancelled"
                            :id "Ir8Uydu4B1MkgV4urVsF"
                            :gas_price 294
                            :delivery_fee 599
                            :time_window_end 1476766420
                            :paid false
                            :lat 32.78127163485819
                            :address_zip "92109"
                            :gallons 15.0
                            :time_window_start 1476762820
                            :address_street "Bayside Walk Pacific Beach"
                            :license_plate "VVHH"
                            :lng -117.23440447031247}
                           {:tire_pressure_fillup false
                            :vehicle_id "KmHDbvxQYKjcCX7EPJzM"
                            :total_price 2650
                            :octane "87"
                            :special_instructions ""
                            :status "assigned"
                            :id "aPlWLjXF2EWCYo1hS6LV"
                            :gas_price 299
                            :delivery_fee 0
                            :time_window_end 1476773512
                            :paid false
                            :lat 34.01338262284055
                            :address_zip "90066"
                            :gallons 15.0
                            :time_window_start 1476762712
                            :address_street "3388 S Centinela Ave"
                            :license_plate "VVHH"
                            :lng -118.43998232065427}
                           {:tire_pressure_fillup false
                            :vehicle_id "KmHDbvxQYKjcCX7EPJzM"
                            :total_price 4485
                            :octane "87"
                            :special_instructions ""
                            :status "complete"
                            :id "7RSx4t1YjryzyutZKqhH"
                            :gas_price 299
                            :delivery_fee 0
                            :time_window_end 1476773445
                            :paid false
                            :lat 34.023484704499175
                            :address_zip "90034"
                            :gallons 15.0
                            :time_window_start 1476762645
                            :address_street "10741 Westminster Ave"
                            :license_plate "VVHH"
                            :lng -118.41234483896481}]})))

  (testing "getting orders list with invalid params"
    (is (=submap (mock-get-with-auth "orders/get"
                                     {:limit 3
                                      :start 5
                                      :sort "descending"
                                      :vehicle_id "KmHDbvxQYKjcCX7EPJzM"})
                 {:success false
                  :code "invalid-input"})))
  
  (testing "order request"
    (is (:success (mock-post-with-auth "orders/request"
                                       {:lat "33.995632"
                                        :lng "-118.474990"
                                        :vehicle_id "KmHDbvxQYKjcCX7EPJzM"
                                        :time_limit 180
                                        :gallons 15
                                        :gas_price 312
                                        :delivery_fee 399}))))

  (testing "order request invalid lat lng"
    (is (=submap (mock-post-with-auth "orders/request"
                                      {:lat "3.995632"
                                       :lng "-118.474990"
                                       :vehicle_id "KmHDbvxQYKjcCX7EPJzM"
                                       :time_limit 180
                                       :gallons 15
                                       :gas_price 312
                                       :delivery_fee 399})
                 {:success false
                  :code "invalid-latlng"})))

  (testing "order request wrong price"
    (is (=submap (mock-post-with-auth "orders/request"
                                      {:lat "33.995632"
                                       :lng "-118.474990"
                                       :vehicle_id "KmHDbvxQYKjcCX7EPJzM"
                                       :time_limit 180
                                       :gallons 15
                                       :gas_price 345
                                       :delivery_fee 399})
                 {:success false
                  :code "invalid-price"})))

  (testing "order request wrong time choice for price"
    (is (=submap (mock-post-with-auth "orders/request"
                                      {:lat "33.995632"
                                       :lng "-118.474990"
                                       :vehicle_id "KmHDbvxQYKjcCX7EPJzM"
                                       :time_limit 60
                                       :gallons 15
                                       :gas_price 312
                                       :delivery_fee 399})
                 {:success false
                  :code "invalid-price"})))

  ;; (testing "order request unavailable time choice"
  ;;   (is (=submap (mock-post-with-auth "orders/request"
  ;;                                     {:lat "33.995632"
  ;;                                      :lng "-118.474990"
  ;;                                      :vehicle_id "KmHDbvxQYKjcCX7EPJzM"
  ;;                                      :time_limit 60
  ;;                                      :gallons 15
  ;;                                      :gas_price 312
  ;;                                      :delivery_fee 599})
  ;;                {:success false
  ;;                 :code "invalid-pricsse"})))
  )

(deftest vehicles-routes
  (testing "get all of a user's vehicles"
    (is (=submap (mock-get-with-auth "vehicles/get" {})
                 {:success true,
                  :vehicles
                  [{:id "KmHDbvxQYKjcCX7EPJzM",
                    :year "1990",
                    :make "Buick",
                    :model "Regal",
                    :color "Silver",
                    :license_plate "VVHH",
                    :octane "87"}]}))))
