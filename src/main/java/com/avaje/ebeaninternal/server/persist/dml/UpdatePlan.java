package com.avaje.ebeaninternal.server.persist.dml;

import com.avaje.ebean.bean.EntityBean;
import com.avaje.ebeaninternal.api.ConcurrencyMode;
import com.avaje.ebeaninternal.api.SpiUpdatePlan;
import com.avaje.ebeaninternal.server.persist.dmlbind.Bindable;

import java.sql.SQLException;

/**
 * Plan for executing bean updates for a given set of changed properties.
 */
public class UpdatePlan implements SpiUpdatePlan {

  private final String key;

  private final ConcurrencyMode mode;

  private final String sql;

  private final Bindable set;

  private final long timeCreated;

  private final boolean emptySetClause;

  private long timeLastUsed;

  /**
   * Create a non cached UpdatePlan.
   */
  UpdatePlan(ConcurrencyMode mode, String sql, Bindable set) {
    this(null, mode, sql, set);
  }

  /**
   * Create a UpdatePlan with a given key.
   */
  UpdatePlan(String key, ConcurrencyMode mode, String sql, Bindable set) {
    this.emptySetClause = (sql == null);
    this.key = key;
    this.mode = mode;
    this.sql = sql;
    this.set = set;
    this.timeCreated = System.currentTimeMillis();
  }

  public boolean isEmptySetClause() {
    return emptySetClause;
  }

  /**
   * Run the prepared statement binding for the 'update set' properties.
   */
  public void bindSet(DmlHandler bind, EntityBean bean) throws SQLException {
    set.dmlBind(bind, bean);
    // atomic on 64 bit jvm
    this.timeLastUsed = System.currentTimeMillis();
  }

  /**
   * Return the time this plan was created.
   */
  public long getTimeCreated() {
    return timeCreated;
  }

  /**
   * Return the time this plan was last used.
   */
  public long getTimeLastUsed() {
    return timeLastUsed;
  }

  /**
   * Return the key.
   */
  public String getKey() {
    return key;
  }

  /**
   * Return the concurrency mode for this plan.
   */
  public ConcurrencyMode getMode() {
    return mode;
  }

  /**
   * Return the DML statement.
   */
  public String getSql() {
    return sql;
  }

  /**
   * Return the Bindable properties for the update set.
   */
  public Bindable getSet() {
    return set;
  }

}
