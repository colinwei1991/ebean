package com.avaje.ebeaninternal.server.persist.dml;

import com.avaje.ebean.bean.EntityBean;
import com.avaje.ebeaninternal.api.ConcurrencyMode;
import com.avaje.ebeaninternal.api.SpiUpdatePlan;
import com.avaje.ebeaninternal.server.core.PersistRequestBean;
import com.avaje.ebeaninternal.server.deploy.BeanDescriptor;
import com.avaje.ebeaninternal.server.persist.dmlbind.Bindable;
import com.avaje.ebeaninternal.server.persist.dmlbind.BindableId;
import com.avaje.ebeaninternal.server.persist.dmlbind.BindableList;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Meta data for update handler. The meta data is for a particular bean type. It
 * is considered immutable and is thread safe.
 */
public final class UpdateMeta {

  private final BindableList set;
  private final BindableId id;
  private final Bindable version;

  private final String tableName;

  private final UpdatePlan modeNoneUpdatePlan;
  private final UpdatePlan modeVersionUpdatePlan;

  private final boolean emptyStringAsNull;

  public UpdateMeta(boolean emptyStringAsNull, BeanDescriptor<?> desc, BindableList set, BindableId id, Bindable version) {
    this.emptyStringAsNull = emptyStringAsNull;
    this.tableName = desc.getBaseTable();
    this.set = set;
    this.id = id;
    this.version = version;

    String sqlNone = genSql(ConcurrencyMode.NONE, set, desc.getBaseTable());
    String sqlVersion = genSql(ConcurrencyMode.VERSION, set, desc.getBaseTable());

    this.modeNoneUpdatePlan = new UpdatePlan(ConcurrencyMode.NONE, sqlNone, set);
    this.modeVersionUpdatePlan = new UpdatePlan(ConcurrencyMode.VERSION, sqlVersion, set);
  }

  /**
   * Return true if empty strings should be treated as null.
   */
  public boolean isEmptyStringAsNull() {
    return emptyStringAsNull;
  }

  /**
   * Return the base table name.
   */
  public String getTableName() {
    return tableName;
  }

  /**
   * Bind the request based on the concurrency mode.
   */
  public void bind(PersistRequestBean<?> persist, DmlHandler bind, SpiUpdatePlan updatePlan) throws SQLException {

    EntityBean bean = persist.getEntityBean();

    updatePlan.bindSet(bind, bean);

    id.dmlBind(bind, bean);

    switch (persist.getConcurrencyMode()) {
      case VERSION:
        version.dmlBind(bind, bean);
        break;

      default:
        break;
    }
  }

  /**
   * get or generate the sql based on the concurrency mode.
   */
  public SpiUpdatePlan getUpdatePlan(PersistRequestBean<?> request) {

    if (request.isDynamicUpdateSql()) {
      return getDynamicUpdatePlan(request);
    }

    // 'full bean' update...
    ConcurrencyMode mode = request.determineConcurrencyMode();
    switch (mode) {
      case NONE:
        return modeNoneUpdatePlan;

      case VERSION:
        return modeVersionUpdatePlan;

      default:
        throw new RuntimeException("Invalid mode " + mode);
    }
  }

  private SpiUpdatePlan getDynamicUpdatePlan(PersistRequestBean<?> persistRequest) {

    String key = persistRequest.getUpdatePlanHash();

    // check if we can use a cached UpdatePlan
    BeanDescriptor<?> beanDescriptor = persistRequest.getBeanDescriptor();
    SpiUpdatePlan updatePlan = beanDescriptor.getUpdatePlan(key);
    if (updatePlan != null) {
      return updatePlan;
    }

    // build a new UpdatePlan and cache it

    // build a bindableList that only contains the changed properties
    List<Bindable> list = new ArrayList<>();
    set.addToUpdate(persistRequest, list);
    BindableList bindableList = new BindableList(list);

    ConcurrencyMode mode = persistRequest.determineConcurrencyMode();

    // build the SQL for this update statement
    String sql = genSql(mode, bindableList, persistRequest.getUpdateTable());

    updatePlan = new UpdatePlan(key, mode, sql, bindableList);

    // add the UpdatePlan to the cache
    beanDescriptor.putUpdatePlan(key, updatePlan);

    return updatePlan;
  }

  private String genSql(ConcurrencyMode conMode, BindableList bindableList, String tableName) {

    // update set col0=?, col1=?, col2=? where bcol=? and bc1=? and bc2=?

    GenerateDmlRequest request = new GenerateDmlRequest();
    request.append("update ").append(tableName).append(" set ");

    request.setUpdateSetMode();
    bindableList.dmlAppend(request);

    if (request.getBindColumnCount() == 0) {
      // update properties must have been updatable=false
      // with the result that nothing is in the set clause
      return null;
    }

    request.append(" where ");

    request.setWhereIdMode();
    id.dmlAppend(request);

    if (ConcurrencyMode.VERSION.equals(conMode)) {
      if (version == null) {
        return null;
      }
      version.dmlAppend(request);
    }

    return request.toString();
  }

}
