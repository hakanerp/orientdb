package com.orientechnologies.orient.core.sql.executor;

import com.orientechnologies.common.concur.OTimeoutException;
import com.orientechnologies.orient.core.command.OBasicCommandContext;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.sql.parser.OIdentifier;
import com.orientechnologies.orient.core.sql.parser.OLocalResultSet;
import com.orientechnologies.orient.core.sql.parser.OStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luigidellaquila on 03/08/16.
 */
public class GlobalLetQueryStep extends AbstractExecutionStep {

  private final OIdentifier varName;
  private final OStatement  query;

  boolean executed = false;

  public GlobalLetQueryStep(OIdentifier varName, OStatement query, OCommandContext ctx) {
    super(ctx);
    this.varName = varName;
    this.query = query;
  }

  @Override public OTodoResultSet syncPull(OCommandContext ctx, int nRecords) throws OTimeoutException {
    getPrev().ifPresent(x -> x.syncPull(ctx, nRecords));
    calculate(ctx);
    return new OInternalResultSet();
  }

  private void calculate(OCommandContext ctx) {
    if (executed) {
      return;
    }
    OBasicCommandContext subCtx = new OBasicCommandContext();
    subCtx.setDatabase(ctx.getDatabase());
    subCtx.setParent(ctx);
    OInternalExecutionPlan subExecutionPlan = query.createExecutionPlan(subCtx);
    //TODO lazy?
    ctx.setVariable(varName.getStringValue(), toList(new OLocalResultSet(subExecutionPlan)));
    executed = true;
  }

  private List<OResult> toList(OLocalResultSet oLocalResultSet) {
    List<OResult> result = new ArrayList<>();
    while (oLocalResultSet.hasNext()) {
      result.add(oLocalResultSet.next());
    }
    oLocalResultSet.close();
    return result;
  }

  @Override public void asyncPull(OCommandContext ctx, int nRecords, OExecutionCallback callback) throws OTimeoutException {

  }

  @Override public void sendResult(Object o, Status status) {

  }

  @Override public String prettyPrint(int depth, int indent) {
    String spaces = OExecutionStepInternal.getIndent(depth, indent);
    return spaces + "+ LET (once)\n" +
        spaces + "  " + varName + " = (" + query + ")";
  }
}
