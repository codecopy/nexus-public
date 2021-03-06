/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.storage;

import java.util.List;
import java.util.function.BooleanSupplier;

import javax.inject.Named;

import org.sonatype.nexus.common.entity.EntityId;
import org.sonatype.nexus.common.stateguard.Guarded;
import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.transaction.TransactionalDeleteBlob;
import org.sonatype.nexus.transaction.UnitOfWork;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.partition;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.STARTED;

/**
 * A default implementation of {@link ComponentMaintenance} for repository format that don't need
 * additional bookkeeping.
 *
 * @since 3.0
 */
@Named
public class DefaultComponentMaintenanceImpl
    extends FacetSupport
    implements ComponentMaintenance
{
  /**
   * Deletes the component directly, with no additional bookkeeping.
   */
  @Override
  public void deleteComponent(final EntityId componentId) {
    deleteComponent(componentId, true);
  }

  /**
   * Deletes the component directly, with no additional bookkeeping.
   */
  @Override
  public void deleteComponent(final EntityId componentId, final boolean deleteBlobs) {
    checkNotNull(componentId);
    UnitOfWork.begin(getRepository().facet(StorageFacet.class).txSupplier());
    try {
      deleteComponentTx(componentId, deleteBlobs);
    }
    finally {
      UnitOfWork.end();
    }
  }

  @TransactionalDeleteBlob
  protected void deleteComponentTx(final EntityId componentId, final boolean deleteBlobs) {
    StorageTx tx = UnitOfWork.currentTx();
    Component component = tx.findComponentInBucket(componentId, tx.findBucket(getRepository()));
    if (component == null) {
      return;
    }
    log.debug("Deleting component: {}", component.toStringExternal());
    tx.deleteComponent(component, deleteBlobs);
  }

  /**
   * Deletes the asset directly, with no additional bookkeeping.
   */
  @Override
  @Guarded(by = STARTED)
  public void deleteAsset(final EntityId assetId) {
    deleteAsset(assetId, true);
  }

  @Override
  @Guarded(by = STARTED)
  public void deleteAsset(final EntityId assetId, final boolean deleteBlob) {
    checkNotNull(assetId);
    UnitOfWork.begin(getRepository().facet(StorageFacet.class).txSupplier());
    try {
      deleteAssetTx(assetId, deleteBlob);
    }
    finally {
      UnitOfWork.end();
    }
  }

  @Override
  public long deleteComponents(final Iterable<EntityId> components,
                               final BooleanSupplier cancelledCheck,
                               final int batchSize) 
  {
    checkNotNull(components);
    checkNotNull(cancelledCheck);

    UnitOfWork.beginBatch(getRepository().facet(StorageFacet.class).txSupplier());
    long count = 0L;

    try {
      Iterable<List<EntityId>> split = partition(components, batchSize);
      for (List<EntityId> entityIds : split) {
        
        if (cancelledCheck.getAsBoolean()) {
          break;
        }

        count += doBatchDelete(entityIds, cancelledCheck);
      }
    }
    finally {
      UnitOfWork.end();
    }

    after();

    return count;
  }

  @TransactionalDeleteBlob
  protected long deleteComponentBatch(final Iterable<EntityId> components, final BooleanSupplier cancelledCheck) {
    long count = 0L;

    for (EntityId component : components) {
      if (!cancelledCheck.getAsBoolean()) {
        try {
          deleteComponentTx(component, true);

          log.debug("Component with ID '{}' deleted from repository {}", component, getRepository());

          count++;
        }
        catch (Exception e ) {
          log.debug("Unable to delete component with ID {}", component, e);
        }
      }
    }

    return count;
  }

  @TransactionalDeleteBlob
  protected void deleteAssetTx(final EntityId assetId, final boolean deleteBlob) {
    StorageTx tx = UnitOfWork.currentTx();
    Asset asset = tx.findAsset(assetId, tx.findBucket(getRepository()));
    if (asset == null) {
      return;
    }
    log.info("Deleting asset: {}", asset);
    tx.deleteAsset(asset, deleteBlob);
  }

  protected long doBatchDelete(final List<EntityId> entityIds, final BooleanSupplier cancelledCheck) {
    return deleteComponentBatch(entityIds, cancelledCheck);
  }

  @Override
  public void after() {
    //no op
  }
}
