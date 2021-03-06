/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.studio.service.data.exporter;

import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.db.mapper.Mapper;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaAction;
import com.axelor.meta.db.MetaView;
import com.axelor.meta.db.repo.MetaActionRepository;
import com.axelor.meta.db.repo.MetaViewRepository;
import com.axelor.meta.loader.XMLViews;
import com.axelor.meta.schema.ObjectViews;
import com.axelor.meta.schema.actions.Action;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.View;
import com.axelor.meta.schema.views.AbstractWidget;
import com.axelor.meta.schema.views.Dashboard;
import com.axelor.meta.schema.views.Dashlet;
import com.axelor.studio.service.data.CommonService;
import com.axelor.studio.service.data.TranslationService;
import com.google.inject.Inject;

import java.lang.invoke.MethodHandles;

public class DashboardExporter {
	
	private final static Logger log = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );
	
	@Inject
	private MetaViewRepository metaViewRepo;
	
	@Inject
	private TranslationService translationService;
	
	@Inject
	private MetaActionRepository metaActionRepo;
	
	public void export(ExporterService exporterService, String name) throws JAXBException {
		
		MetaView view =  metaViewRepo.all().filter(
				"self.type = 'dashboard'  and self.name = ?", 
				name).fetchOne();
		
		if (view != null) {
			
			ObjectViews views = XMLViews.fromXML(view.getXml());
			
			Dashboard dashboard = (Dashboard) views.getViews().get(0);
			
			for (AbstractWidget widget : dashboard.getItems()) {
				String[] values = processDashlet((Dashlet)widget, view.getModule(), null, name, null, null);
				if (values != null) {
					exporterService.writeRow(values, true);
				}
			}
			
		}
		
	}
	
	public String[] processDashlet(Dashlet dashlet, String module, String model, String view, Mapper mapper, String parentModule) {
		
		String title = dashlet.getTitle();
		String action = dashlet.getAction();
		
		if (title == null && action != null) {
			Action metaAction = MetaStore.getAction(dashlet.getAction());
			if (metaAction instanceof ActionView) {
				title = ((ActionView) metaAction).getTitle();
			}
		}
		
		String[] values = null;
		if (title != null || dashlet.getName() != null) {
			values = new String[CommonService.HEADERS.length];
			values[CommonService.MODULE] = module;
			values[CommonService.MODEL] = model; 
			values[CommonService.VIEW] =	view; 
			values[CommonService.TITLE] = title;
			values[CommonService.TITLE_FR] = translationService.getTranslation(title, "fr");
			values[CommonService.IF_CONFIG] = dashlet.getConditionToCheck();
			values[CommonService.IF_MODULE] = ExporterService.getModuleToCheck(dashlet, parentModule);
			values[CommonService.NAME] = dashlet.getAction();
			
			if (dashlet.getColSpan() != null) {
				values[CommonService.COLSPAN] = dashlet.getColSpan().toString();
			}
			
			values = addDashletAction(values, dashlet);
		}
		
		return values;
	}

	private String[] addDashletAction(String[] values, Dashlet dashlet) {
		
		String type = "dashlet";
		
		MetaAction metaAction = metaActionRepo.all().filter("self.name = ?1", dashlet.getAction()).fetchOne();
		
		if (metaAction == null || !metaAction.getType().equals("action-view") || metaAction.getModel() == null) {
			values[CommonService.TYPE] = type;
			return values;
		}
		
		try {
			ActionView action = (ActionView) XMLViews.fromXML(metaAction.getXml()).getActions().get(0);
			log.debug("Processing action: {}", action.getName());
			if (action.getModel() == null) {
				values[CommonService.TYPE] = type;
				return values;
			}
			String[] model = action.getModel().split("\\.");
			type = type + "(" + model[model.length - 1] + ")";
			for (View view : action.getViews()) {
				if (view.getName() != null) {
					type += "," + view.getName();
				}
			}
			values[CommonService.TYPE] = type;
			values[CommonService.DOMAIN] = action.getDomain();
		} catch (JAXBException e) {

		}
		
		return values;
	}
}
