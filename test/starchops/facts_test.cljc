(ns starchops.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [starchops.facts :as facts]))

;; ──────────────────────── Product Type Lookups ──────────────────────

(deftest product-type-by-id-test
  (testing "native corn starch product type exists"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (some? p))
      (is (= (:id p) :starch/corn-native))
      (is (= (:moisture-target-percent p) 12.0))
      (is (= (:sulfite-residue-max-ppm p) 50))))

  (testing "native potato starch product type exists"
    (let [p (facts/product-type-by-id :starch/potato-native)]
      (is (some? p))
      (is (= (:purity-min-percent p) 98.5))
      (is (= (:sulfite-residue-max-ppm p) 10))))

  (testing "native cassava starch product type has the strictest microbial limit"
    (let [p (facts/product-type-by-id :starch/cassava-native)]
      (is (some? p))
      (is (= (:microbial-load-max-cfu p) 500))))

  (testing "native wheat starch product type exists"
    (let [p (facts/product-type-by-id :starch/wheat-native)]
      (is (some? p))
      (is (= (:purity-min-percent p) 98.0))))

  (testing "nonexistent product type returns nil"
    (is (nil? (facts/product-type-by-id :starch/nonexistent)))))

;; ──────────────────────── Jurisdiction Lookups ──────────────────────

(deftest jurisdiction-by-id-test
  (testing "JP prefectural jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)]
      (is (some? j))
      (is (true? (:allergen-declaration-required j)))
      (is (contains? (:major-allergens j) :wheat))))

  (testing "US FDA jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :us/fda)]
      (is (some? j))
      (is (contains? (set (:required-evidence j)) :sulfite-residue-test))))

  (testing "EU EFSA jurisdiction requires a microbial test"
    (let [j (facts/jurisdiction-by-id :eu/efsa)]
      (is (some? j))
      (is (contains? (set (:required-evidence j)) :microbial-test))))

  (testing "nonexistent jurisdiction returns nil"
    (is (nil? (facts/jurisdiction-by-id :xx/unknown)))))

;; ──────────────────────── Allergen Lookups ──────────────────────

(deftest raw-material-allergens-test
  (testing "wheat starch grade has wheat allergen"
    (let [a (facts/raw-material-allergens :wheat/starch-grade)]
      (is (= (:primary-allergen a) :wheat))))

  (testing "yellow dent corn has no primary allergen"
    (let [a (facts/raw-material-allergens :corn/yellow-dent)]
      (is (nil? (:primary-allergen a)))
      (is (empty? (:cross-contact-risk a)))))

  (testing "waxy corn hybrid has no primary allergen but carries wheat cross-contact risk"
    (let [a (facts/raw-material-allergens :corn/waxy-hybrid)]
      (is (nil? (:primary-allergen a)))
      (is (contains? (:cross-contact-risk a) :wheat))))

  (testing "nonexistent raw material returns nil"
    (is (nil? (facts/raw-material-allergens :unknown/material)))))

;; ──────────────────────── Extraction Safety Predicates ──────────────────────

(deftest moisture-in-range-test
  (testing "moisture within tolerance passes"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (true? (facts/moisture-in-range? 12.0 p)))))

  (testing "moisture at lower tolerance boundary passes"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (true? (facts/moisture-in-range? 11.5 p)))))

  (testing "moisture below range fails"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (false? (facts/moisture-in-range? 11.0 p)))))

  (testing "moisture above range fails"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (false? (facts/moisture-in-range? 13.0 p))))))

(deftest purity-in-range-test
  (testing "purity within range passes"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (true? (facts/purity-in-range? 99.5 p)))))

  (testing "purity below minimum fails"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (false? (facts/purity-in-range? 95.0 p)))))

  (testing "purity above maximum fails"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (false? (facts/purity-in-range? 101.0 p))))))

(deftest granulation-in-range-test
  (testing "granulation within range passes"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (true? (facts/granulation-in-range? 15 p)))))

  (testing "granulation below minimum fails"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (false? (facts/granulation-in-range? 2 p)))))

  (testing "granulation above maximum fails"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (false? (facts/granulation-in-range? 40 p))))))

(deftest sulfite-residue-in-range-test
  (testing "sulfite residue at or below the max passes"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (true? (facts/sulfite-residue-in-range? 50 p)))
      (is (true? (facts/sulfite-residue-in-range? 10 p)))))

  (testing "sulfite residue above the max fails"
    (let [p (facts/product-type-by-id :starch/corn-native)]
      (is (false? (facts/sulfite-residue-in-range? 60 p))))))

(deftest microbial-load-in-range-test
  (testing "microbial load at or below the max passes"
    (let [p (facts/product-type-by-id :starch/cassava-native)]
      (is (true? (facts/microbial-load-in-range? 500 p)))
      (is (true? (facts/microbial-load-in-range? 10 p)))))

  (testing "microbial load above the max fails"
    (let [p (facts/product-type-by-id :starch/cassava-native)]
      (is (false? (facts/microbial-load-in-range? 600 p))))))

;; ──────────────────────── Allergen Traceability ──────────────────────

(deftest raw-material-allergen-set-test
  (testing "wheat-only formulation collects wheat allergen"
    (let [raw-materials [:wheat/starch-grade]
          allergens (facts/raw-material-allergen-set raw-materials)]
      (is (contains? allergens :wheat))))

  (testing "corn/potato/cassava formulation produces no primary allergen"
    (let [raw-materials [:corn/yellow-dent :potato/starch-grade :cassava/root]
          allergens (facts/raw-material-allergen-set raw-materials)]
      (is (empty? allergens))))

  (testing "waxy corn alone contributes no primary allergen (only cross-contact risk, informational)"
    (let [raw-materials [:corn/waxy-hybrid]
          allergens (facts/raw-material-allergen-set raw-materials)]
      (is (empty? allergens))))

  (testing "blended wheat + corn formulation includes wheat allergen"
    (let [raw-materials [:wheat/starch-grade :corn/yellow-dent]
          allergens (facts/raw-material-allergen-set raw-materials)]
      (is (= allergens #{:wheat})))))

(deftest allergen-declaration-complete-test
  (testing "declaration matches formulation for jurisdiction"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          raw-materials [:wheat/starch-grade]
          declared #{:wheat}]
      (is (true? (facts/allergen-declaration-complete? j raw-materials declared)))))

  (testing "incomplete declaration fails"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          raw-materials [:wheat/starch-grade]
          declared #{}]
      (is (false? (facts/allergen-declaration-complete? j raw-materials declared)))))

  (testing "extra declarations pass (conservative)"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          raw-materials [:corn/yellow-dent]
          declared #{:wheat}]
      (is (true? (facts/allergen-declaration-complete? j raw-materials declared))))))

;; ──────────────────────── Evidence Completeness ──────────────────────

(deftest required-evidence-satisfied-test
  (testing "complete evidence checklist passes"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          evidence [:raw-material-intake-record :extraction-log :moisture-test
                    :purity-test :sulfite-residue-test :microbial-test
                    :allergen-declaration :weight-check]]
      (is (true? (facts/required-evidence-satisfied? j evidence)))))

  (testing "incomplete evidence fails"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          evidence [:raw-material-intake-record :extraction-log]]
      (is (false? (facts/required-evidence-satisfied? j evidence))))))
