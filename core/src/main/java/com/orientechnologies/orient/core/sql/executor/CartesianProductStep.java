package com.orientechnologies.orient.core.sql.executor;

import com.orientechnologies.common.concur.OTimeoutException;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.sql.parser.OLocalResultSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by luigidellaquila on 11/10/16.
 */
public class CartesianProductStep extends AbstractExecutionStep {

  private List<OInternalExecutionPlan> subPlans = new ArrayList<>();

  private boolean inited = false;
  List<Boolean>            completedPrefetch = new ArrayList<>();
  List<OInternalResultSet> preFetches        = new ArrayList<>();//consider using resultset.reset() instead of buffering

  List<OResultSet> resultSets   = new ArrayList<>();
  List<OResult>    currentTuple = new ArrayList<>();

  OResultInternal nextRecord;

  public CartesianProductStep(OCommandContext ctx) {
    super(ctx);
  }

  @Override public OResultSet syncPull(OCommandContext ctx, int nRecords) throws OTimeoutException {
    getPrev().ifPresent(x -> x.syncPull(ctx, nRecords));
    init(ctx);
    //    return new OInternalResultSet();
    return new OResultSet() {
      int currentCount = 0;

      @Override public boolean hasNext() {
        if (currentCount >= nRecords) {
          return false;
        }
        return nextRecord != null;
      }

      @Override public OResult next() {
        if (currentCount >= nRecords || nextRecord == null) {
          throw new IllegalStateException();
        }
        OResultInternal result = nextRecord;
        fetchNextRecord();
        currentCount++;
        return result;
      }

      @Override public void close() {

      }

      @Override public Optional<OExecutionPlan> getExecutionPlan() {
        return null;
      }

      @Override public Map<String, Long> getQueryStats() {
        return null;
      }
    };
    //    throw new UnsupportedOperationException("cartesian product is not yet implemented in MATCH statement");
    //TODO
  }

  private void init(OCommandContext ctx) {
    if (subPlans == null || subPlans.isEmpty()) {
      return;
    }
    if (inited) {
      return;
    }

    for (OInternalExecutionPlan plan : subPlans) {
      resultSets.add(new OLocalResultSet(plan));
      this.preFetches.add(new OInternalResultSet());
    }
    fetchFirstRecord();
    inited = true;
  }

  private void fetchFirstRecord() {
    int i = 0;
    for (OResultSet rs : resultSets) {
      if (!rs.hasNext()) {
        nextRecord = null;
        return;
      }
      OResult item = rs.next();
      currentTuple.add(item);
      completedPrefetch.add(false);
    }
    buildNextRecord();
  }

  private void fetchNextRecord() {
    fetchNextRecord(resultSets.size() - 1);
  }

  private void fetchNextRecord(int level) {
    OResultSet currentRs = resultSets.get(level);
    if (!currentRs.hasNext()) {
      if (level <= 0) {
        nextRecord = null;
        currentTuple = null;
        return;
      }
      currentRs = preFetches.get(level);
      currentRs.reset();
      resultSets.set(level, currentRs);
      currentTuple.set(level, currentRs.next());
      fetchNextRecord(level - 1);
    } else {
      currentTuple.set(level, currentRs.next());
    }
    buildNextRecord();
  }

  private void buildNextRecord() {
    if (currentTuple == null) {
      nextRecord = null;
      return;
    }
    nextRecord = new OResultInternal();

    for (int i = 0; i < this.currentTuple.size(); i++) {
      OResult res = this.currentTuple.get(i);
      for (String s : res.getPropertyNames()) {
        nextRecord.setProperty(s, res.getProperty(s));
      }
      if (!completedPrefetch.get(i)) {
        preFetches.get(i).add(res);
        if (!resultSets.get(i).hasNext()) {
          completedPrefetch.set(i, true);
        }
      }
    }
  }

  @Override public void asyncPull(OCommandContext ctx, int nRecords, OExecutionCallback callback) throws OTimeoutException {

  }

  @Override public void sendResult(Object o, Status status) {

  }

  public void addSubPlan(OInternalExecutionPlan subPlan) {
    this.subPlans.add(subPlan);
  }

  @Override public String prettyPrint(int depth, int indent) {
    String result = "";
    String ind = OExecutionStepInternal.getIndent(depth, indent);

    int[] blockSizes = new int[subPlans.size()];

    for (int i = 0; i < subPlans.size(); i++) {
      OInternalExecutionPlan currentPlan = subPlans.get(subPlans.size() - 1 - i);
      String partial = currentPlan.prettyPrint(0, indent);

      String[] partials = partial.split("\n");
      blockSizes[subPlans.size() - 1 - i] = partials.length + 2;
      result = "+-------------------------\n" + result;
      for (int j = 0; j < partials.length; j++) {
        String p = partials[partials.length - 1 - j];
        if (result.length() > 0) {
          result = appendPipe(p) + "\n" + result;
        } else {
          result = appendPipe(p);
        }
      }
      result = "+-------------------------\n" + result;
    }
    result = addArrows(result, blockSizes);
    result += foot(blockSizes);
    result = ind + result;
    result = result.replaceAll("\n", "\n" + ind);
    result = head(depth, indent, subPlans.size()) + "\n" + result;
    return result;
  }

  private String addArrows(String input, int[] blockSizes) {
    String result = "";
    String[] rows = input.split("\n");
    int rowNum = 0;
    for (int block = 0; block < blockSizes.length; block++) {
      int blockSize = blockSizes[block];
      for (int subRow = 0; subRow < blockSize; subRow++) {
        for (int col = 0; col < blockSizes.length * 3; col++) {
          if (isHorizontalRow(col, subRow, block, blockSize)) {
            result += "-";
          } else if (isPlus(col, subRow, block, blockSize)) {
            result += "+";
          } else if (isVerticalRow(col, subRow, block, blockSize)) {
            result += "|";
          } else {
            result += " ";
          }
        }
        result += rows[rowNum] + "\n";
        rowNum++;
      }
    }

    return result;
  }

  private boolean isHorizontalRow(int col, int subRow, int block, int blockSize) {
    if (col < block * 3 + 2) {
      return false;
    }
    if (subRow == blockSize / 2) {
      return true;
    }
    return false;
  }

  private boolean isPlus(int col, int subRow, int block, int blockSize) {
    if (col == block * 3 + 1) {
      if (subRow == blockSize / 2) {
        return true;
      }
    }
    return false;
  }

  private boolean isVerticalRow(int col, int subRow, int block, int blockSize) {
    if (col == block * 3 + 1) {
      if (subRow > blockSize / 2) {
        return true;
      }
    } else if (col < block * 3 + 1 && col % 3 == 1) {
      return true;
    }

    return false;
  }

  private String head(int depth, int indent, int nItems) {
    String ind = OExecutionStepInternal.getIndent(depth, indent);
    return ind + "+ CARTESIAN PRODUCT";
  }

  private String foot(int[] blockSizes) {
    String result = "";
    for (int i = 0; i < blockSizes.length; i++) {
      result += " V ";//TODO
    }
    return result;
  }

  private String spaces(int num) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < num; i++) {
      result.append(" ");
    }
    return result.toString();
  }

  private String appendPipe(String p) {
    return "| " + p;
  }
}
