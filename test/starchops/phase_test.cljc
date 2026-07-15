(ns starchops.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [starchops.phase :as phase]))

;; ──────────────────────── Phase Validity ──────────────────────

(deftest valid-phase-test
  (testing "intake is valid"
    (is (true? (phase/valid-phase? :intake))))

  (testing "steep is valid"
    (is (true? (phase/valid-phase? :steep))))

  (testing "extract is valid"
    (is (true? (phase/valid-phase? :extract))))

  (testing "archived is valid"
    (is (true? (phase/valid-phase? :archived))))

  (testing "invalid phase returns false"
    (is (false? (phase/valid-phase? :invalid)))))

;; ──────────────────────── Phase Transitions ──────────────────────

(deftest can-transition-test
  (testing "intake -> steep is valid (forward progression)"
    (is (true? (phase/can-transition? :intake :steep))))

  (testing "intake -> extract is valid (skip steep)"
    (is (true? (phase/can-transition? :intake :extract))))

  (testing "steep -> intake is invalid (backward)"
    (is (false? (phase/can-transition? :steep :intake))))

  (testing "extract -> refine is valid (forward progression)"
    (is (true? (phase/can-transition? :extract :refine))))

  (testing "refine -> archived is valid (forward to end)"
    (is (true? (phase/can-transition? :refine :archived))))

  (testing "archived -> intake is invalid (backward from end)"
    (is (false? (phase/can-transition? :archived :intake))))

  (testing "same phase is invalid"
    (is (false? (phase/can-transition? :extract :extract))))

  (testing "invalid phases return false"
    (is (false? (phase/can-transition? :invalid :extract)))
    (is (false? (phase/can-transition? :extract :invalid)))))
