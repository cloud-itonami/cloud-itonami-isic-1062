(ns starchops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [starchops.governor :as governor]))

(def ^:private now-ms #?(:clj (System/currentTimeMillis) :cljs (.now js/Date)))
(def ^:private ten-days-ago (- now-ms (* 10 24 60 60 1000)))
(def ^:private hundred-days-ago (- now-ms (* 100 24 60 60 1000)))

(def ^:private clean-batch
  {:product-type :starch/corn-native
   :jurisdiction :jp/prefectural
   :moisture-percent 12.0
   :sulfite-residue-ppm 20
   :microbial-load-cfu 200
   :purity-percent 99.5
   :granulation-microns 15
   :foreign-material-detected? false
   :detection-equipment-last-calibration-date ten-days-ago
   :weight-variance-grams 20
   :raw-materials [:corn/yellow-dent]
   :declared-allergens #{}
   :sanitation-score 85
   :evidence-checklist [:raw-material-intake-record :extraction-log :moisture-test
                        :purity-test :sulfite-residue-test :microbial-test
                        :allergen-declaration :weight-check]})

;; ──────────────────────── Hard Violations ──────────────────────

(deftest spec-basis-violation-test
  (testing "proposal with no jurisdiction citation is a hard violation"
    (let [req {:op :log-production-batch :subject "batch-001"}
          prop {:cites [] :value {:jurisdiction nil}}
          result (governor/check req {:actor-id "gov-1"} prop {})]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :no-spec-basis) (:violations result)))))

  (testing "proposal with proper citation passes spec basis check"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural}}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Moisture Violations ──────────────────────

(deftest moisture-violation-test
  (testing "batch with moisture out of range triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :moisture-percent 10.0)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :moisture-out-of-target) (:violations result)))))

  (testing "batch with moisture in range passes"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Sulfite Residue Violations ──────────────────────

(deftest sulfite-residue-violation-test
  (testing "batch with sulfite residue exceeding the product's limit triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :sulfite-residue-ppm 80)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :sulfite-residue-exceeded) (:violations result)))))

  (testing "potato starch batch has a much stricter sulfite limit than corn starch"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch
                                            :product-type :starch/potato-native
                                            :moisture-percent 18.0
                                            :purity-percent 99.0
                                            :granulation-microns 50
                                            :sulfite-residue-ppm 20)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :sulfite-residue-exceeded) (:violations result))))))

;; ──────────────────────── Microbial Load Violations ──────────────────────

(deftest microbial-load-violation-test
  (testing "batch with microbial load exceeding the product's limit triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :microbial-load-cfu 1500)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :microbial-load-exceeded) (:violations result)))))

  (testing "cassava starch batch has a stricter microbial limit than corn starch"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch
                                            :product-type :starch/cassava-native
                                            :moisture-percent 13.0
                                            :purity-percent 99.0
                                            :granulation-microns 20
                                            :sulfite-residue-ppm 5
                                            :microbial-load-cfu 600)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :microbial-load-exceeded) (:violations result))))))

;; ──────────────────────── Purity Violations ──────────────────────

(deftest purity-violation-test
  (testing "batch with purity out of range triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :purity-percent 90.0)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :purity-out-of-range) (:violations result))))))

;; ──────────────────────── Granulation Violations ──────────────────────

(deftest granulation-violation-test
  (testing "batch with granulation out of range triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :granulation-microns 60)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :granulation-out-of-range) (:violations result))))))

;; ──────────────────────── Foreign Material Violations ──────────────────────

(deftest foreign-material-violation-test
  (testing "batch with detected foreign material triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :foreign-material-detected? true)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :foreign-material-detected) (:violations result))))))

;; ──────────────────────── Detection-Equipment Calibration Violations ──────────────────────

(deftest detection-equipment-calibration-violation-test
  (testing "batch with overdue detection-equipment calibration triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :detection-equipment-last-calibration-date hundred-days-ago)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :detection-equipment-calibration-overdue) (:violations result))))))

;; ──────────────────────── Weight Variance Violations ──────────────────────

(deftest weight-variance-violation-test
  (testing "batch with excessive weight variance triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :weight-variance-grams 75)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :weight-variance-excessive) (:violations result))))))

;; ──────────────────────── Allergen Labeling Violations ──────────────────────

(deftest allergen-label-violation-test
  (testing "batch with undeclared wheat-starch allergen triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch
                                            :raw-materials [:wheat/starch-grade]
                                            :declared-allergens #{})}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :allergen-label-mismatch) (:violations result))))))

;; ──────────────────────── Sanitation Score Violations ──────────────────────

(deftest sanitation-score-violation-test
  (testing "batch with insufficient sanitation score triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :sanitation-score 60)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :sanitation-score-insufficient) (:violations result))))))

;; ──────────────────────── Food-Safety Flag Violations ──────────────────────

(deftest food-safety-flag-unresolved-violation-test
  (testing "batch with an unresolved food-safety flag triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch
                                            :safety-concern-raised? true
                                            :safety-concern-resolved? false)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :food-safety-flag-unresolved) (:violations result)))))

  (testing "batch with a resolved food-safety flag does not trigger this rule"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch
                                            :safety-concern-raised? true
                                            :safety-concern-resolved? true)}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :food-safety-flag-unresolved) (:violations result)))))))

;; ──────────────────────── Escalation (Low Confidence) ──────────────────────

(deftest low-confidence-escalation-test
  (testing "low confidence proposal escalates even when hard checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :schedule-maintenance :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.5}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── High Stakes Escalation ──────────────────────

(deftest high-stakes-escalation-test
  (testing "log-production-batch escalates even when all checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.95}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── Already Processed Violation ──────────────────────

(deftest already-processed-violation-test
  (testing "batch already processed triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:product-type :starch/corn-native
                            :processed? true}}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :already-processed) (:violations result))))))
