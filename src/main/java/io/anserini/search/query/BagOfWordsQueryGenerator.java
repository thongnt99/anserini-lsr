/*
 * Anserini: A Lucene toolkit for reproducible information retrieval research
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.search.query;

import io.anserini.analysis.AnalyzerUtils;
import io.anserini.index.Constants;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * Bag of Terms query builder
 */
public class BagOfWordsQueryGenerator extends QueryGenerator implements  FeatureGenerator {
  @Override
  public Query buildQuery(String field, Analyzer analyzer, String queryText) {
    List<String> tokens = AnalyzerUtils.analyze(analyzer, queryText);
    Map<String, Long> collect = tokens.stream()
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (String t : collect.keySet()) {
      builder.add(new BoostQuery(new TermQuery(new Term(field, t)), (float) collect.get(t)),
          BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  public Query buildFeatureQuery(String field, Analyzer analyzer, String queryText) {
    List<String> tokens = AnalyzerUtils.analyze(analyzer, queryText);
    Map<String, Long> collect = tokens.stream()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    Map<String, Float> normalizedScore = new HashMap<>();
    float maxWeight = 0;
    for (String t : collect.keySet()){
      float s = (float) collect.get(t);
      normalizedScore.put(t, s);
      if (s > maxWeight) {
        maxWeight = s;
      }
    }
    // The maximum weight for FeatureQuery is 64, this constraint could be lifted but might not be necessary.
    // Note: This normalization makes the scores between different queries not comparable
    if (maxWeight > 64){
      for (String t : normalizedScore.keySet()){
        normalizedScore.put(t,normalizedScore.get(t)/maxWeight* (float)64.0);
      }
    }

    for (String t : normalizedScore.keySet()) {
      builder.add(FeatureField.newLinearQuery(Constants.CONTENTS, t, normalizedScore.get(t)),BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  @Override
  public Query buildQuery(Map<String, Float> fields, Analyzer analyzer, String queryText) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fields.entrySet()) {
      String field = entry.getKey();
      float boost = entry.getValue();

      Query clause = buildQuery(field, analyzer, queryText);
      builder.add(new BoostQuery(clause, boost), BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  public Query buildQuery(String field, Map<String, Float> queryTokenWeights) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (String t : queryTokenWeights.keySet()) {
      builder.add(new BoostQuery(new TermQuery(new Term(field, t)), queryTokenWeights.get(t)),
          BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

  public Query buildQuery(Map<String, Float> fields, Map<String, Float> queryTokenWeights) {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    for (Map.Entry<String, Float> entry : fields.entrySet()) {
      String field = entry.getKey();
      float boost = entry.getValue();

      Query clause = buildQuery(field, queryTokenWeights);
      builder.add(new BoostQuery(clause, boost), BooleanClause.Occur.SHOULD);
    }
    return builder.build();
  }

}
