/*
 * Copyright (c) 2015, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.dhis2.android.dashboard.api.controllers;

import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.Select;

import org.dhis2.android.dashboard.api.DhisManager;
import org.dhis2.android.dashboard.api.network.APIException;
import org.dhis2.android.dashboard.api.network.tasks.GetDashboardsTask;
import org.dhis2.android.dashboard.api.persistence.models.Dashboard;
import org.dhis2.android.dashboard.api.persistence.models.DashboardItem;
import org.dhis2.android.dashboard.api.persistence.models.DashboardToItem;
import org.dhis2.android.dashboard.api.persistence.models.DashboardToItem$Table;
import org.dhis2.android.dashboard.api.network.models.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.dhis2.android.dashboard.api.utils.DbUtils.toMap;

public final class GetDashboardsController implements IController<List<Dashboard>> {
    private final DhisManager mDhisManager;
    private final Session mSession;

    public GetDashboardsController(DhisManager dhisManager, Session session) {
        mDhisManager = dhisManager;
        mSession = session;
    }

    @Override
    public List<Dashboard> run() throws APIException {
        Map<String, Dashboard> newBaseDashboards = getNewBaseDashboards();
        Map<String, Dashboard> oldDashboards = getOldFullDashboards();

        List<String> dashboardsToDownload = new ArrayList<>();
        for (String dashboardId : newBaseDashboards.keySet()) {
            Dashboard newDashboard = newBaseDashboards.get(dashboardId);
            Dashboard oldDashboard = oldDashboards.get(dashboardId);

            if (oldDashboard == null) {
                dashboardsToDownload.add(dashboardId);
                continue;
            }

            if (newDashboard.getLastUpdated().isAfter(oldDashboard.getLastUpdated())) {
                dashboardsToDownload.add(dashboardId);
            }
        }

        Map<String, Dashboard> newDashboards = getNewFullDashboards(dashboardsToDownload);
        List<Dashboard> combinedDashboards = new ArrayList<>();
        for (String dashboardId : newBaseDashboards.keySet()) {
            Dashboard newDashboard = newDashboards.get(dashboardId);
            Dashboard oldDashboard = oldDashboards.get(dashboardId);

            if (newDashboard != null) {
                combinedDashboards.add(newDashboard);
                continue;
            }

            if (oldDashboard != null) {
                combinedDashboards.add(oldDashboard);
            }
        }
        return combinedDashboards;
    }

    private Map<String, Dashboard> getNewBaseDashboards() throws APIException {
        return toMap(
                (new GetDashboardsTask(mDhisManager,
                        mSession.getServerUri(), mSession.getCredentials(), null).run()));
    }

    private Map<String, Dashboard> getNewFullDashboards(List<String> ids) throws APIException {
        return toMap(
                (new GetDashboardsTask(mDhisManager,
                        mSession.getServerUri(), mSession.getCredentials(), ids).run()));
    }

    private Map<String, Dashboard> getOldFullDashboards() {
        List<Dashboard> dashboards = Select.all(Dashboard.class);
        if (dashboards == null || dashboards.isEmpty()) {
            return toMap(dashboards);
        }

        for (Dashboard dashboard : dashboards) {
            // reading Dashboards to DashboardItems relationship
            // for particular Dashboard
            List<DashboardToItem> items = Select.all(
                    DashboardToItem.class, Condition.column(
                            DashboardToItem$Table.DASHBOARD_DASHBOARDID).is(dashboard)
            );

            // Reading full sized dashboard items
            // and passing them to dashboards
            for (DashboardToItem item : items) {
                List<DashboardItem> dashboardItems = new ArrayList<>();
                dashboardItems.add(item.getDashboardItem().toModel());
                dashboard.setDashboardItems(dashboardItems);
            }
        }

        return toMap(dashboards);
    }
}