(ns starchops.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [starchops.registry :as registry]))

;; ──────────────────────── Moisture Target ──────────────────────

(deftest moisture-out-of-target-test
  (testing "moisture at target with no tolerance returns false"
    (is (false? (registry/moisture-out-of-target? 12.0 12.0 0.5))))

  (testing "moisture within tolerance range returns false"
    (is (false? (registry/moisture-out-of-target? 11.7 12.0 0.5))))

  (testing "moisture below tolerance returns true (violation)"
    (is (true? (registry/moisture-out-of-target? 11.0 12.0 0.5))))

  (testing "moisture above tolerance returns true (violation)"
    (is (true? (registry/moisture-out-of-target? 12.6 12.0 0.5)))))

;; ──────────────────────── Sulfite Residue ──────────────────────

(deftest sulfite-residue-exceeded-test
  (testing "residue within limit returns false (no violation)"
    (is (false? (registry/sulfite-residue-exceeded? 30 50))))

  (testing "residue at limit returns false"
    (is (false? (registry/sulfite-residue-exceeded? 50 50))))

  (testing "residue exceeding limit returns true (violation)"
    (is (true? (registry/sulfite-residue-exceeded? 51 50)))))

;; ──────────────────────── Microbial Load ──────────────────────

(deftest microbial-load-exceeded-test
  (testing "load within limit returns false (no violation)"
    (is (false? (registry/microbial-load-exceeded? 300 500))))

  (testing "load at limit returns false"
    (is (false? (registry/microbial-load-exceeded? 500 500))))

  (testing "load exceeding limit returns true (violation)"
    (is (true? (registry/microbial-load-exceeded? 501 500)))))

;; ──────────────────────── Purity ──────────────────────

(deftest purity-out-of-range-test
  (testing "purity within range returns false (no violation)"
    (is (false? (registry/purity-out-of-range? 99.5 99.0 100.0))))

  (testing "purity at minimum boundary returns false"
    (is (false? (registry/purity-out-of-range? 99.0 99.0 100.0))))

  (testing "purity at maximum boundary returns false"
    (is (false? (registry/purity-out-of-range? 100.0 99.0 100.0))))

  (testing "purity below minimum returns true (violation)"
    (is (true? (registry/purity-out-of-range? 95.0 99.0 100.0))))

  (testing "purity above maximum returns true (violation)"
    (is (true? (registry/purity-out-of-range? 101.0 99.0 100.0)))))

;; ──────────────────────── Granulation ──────────────────────

(deftest granulation-out-of-range-test
  (testing "granulation within range returns false (no violation)"
    (is (false? (registry/granulation-out-of-range? 15 5 25))))

  (testing "granulation below minimum returns true (violation)"
    (is (true? (registry/granulation-out-of-range? 2 5 25))))

  (testing "granulation above maximum returns true (violation)"
    (is (true? (registry/granulation-out-of-range? 40 5 25)))))

;; ──────────────────────── Detection-Equipment Calibration ──────────────────────

(deftest detection-equipment-calibration-overdue-test
  (testing "recent calibration returns false (no violation)"
    ;; Assume calibrated 20 days ago
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          twenty-days-ago (- now (* 20 24 60 60 1000))]
      (is (false? (registry/detection-equipment-calibration-overdue? twenty-days-ago now)))))

  (testing "overdue calibration returns true (violation)"
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          hundred-days-ago (- now (* 100 24 60 60 1000))]
      (is (true? (registry/detection-equipment-calibration-overdue? hundred-days-ago now))))))

;; ──────────────────────── Weight Variance ──────────────────────

(deftest weight-variance-excessive-test
  (testing "variance within tolerance returns false (no violation)"
    (is (false? (registry/weight-variance-excessive? 45 50))))

  (testing "variance at tolerance returns false"
    (is (false? (registry/weight-variance-excessive? 50 50))))

  (testing "variance exceeding tolerance returns true (violation)"
    (is (true? (registry/weight-variance-excessive? 51 50)))))

;; ──────────────────────── Allergen Labeling ──────────────────────

(deftest allergen-label-risk-test
  (testing "declared allergens match formulation returns false (no risk)"
    (let [formula #{:wheat}
          declared #{:wheat}]
      (is (false? (registry/allergen-label-risk? formula declared)))))

  (testing "declared allergens exceed formulation returns false (conservative)"
    (let [formula #{}
          declared #{:wheat}]
      (is (false? (registry/allergen-label-risk? formula declared)))))

  (testing "formulation allergen undeclared returns true (risk)"
    (let [formula #{:wheat}
          declared #{}]
      (is (true? (registry/allergen-label-risk? formula declared))))))

;; ──────────────────────── Foreign Material ──────────────────────

(deftest foreign-material-detected-test
  (testing "no detection returns false"
    (is (false? (registry/foreign-material-detected? false)))
    (is (false? (registry/foreign-material-detected? nil))))

  (testing "detection returns true"
    (is (true? (registry/foreign-material-detected? true)))))

;; ──────────────────────── Sanitation Score ──────────────────────

(deftest sanitation-score-insufficient-test
  (testing "score at minimum returns false (no violation)"
    (is (false? (registry/sanitation-score-insufficient? 75 75))))

  (testing "score above minimum returns false"
    (is (false? (registry/sanitation-score-insufficient? 85 75))))

  (testing "score below minimum returns true (violation)"
    (is (true? (registry/sanitation-score-insufficient? 74 75)))))
