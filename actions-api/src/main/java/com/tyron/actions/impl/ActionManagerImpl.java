package com.tyron.actions.impl;

import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.annotation.NonNull;

import com.tyron.actions.ActionGroup;
import com.tyron.actions.ActionManager;
import com.tyron.actions.ActionPopupMenu;
import com.tyron.actions.AnAction;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.Presentation;

import java.util.HashMap;
import java.util.Map;

public class ActionManagerImpl extends ActionManager {

    private final Map<String, AnAction> mIdToAction = new HashMap<>();
    private final Map<Object, String> mActionToId = new HashMap<>();

    @Override
    public void fillMenu(Menu menu, String place, boolean isContext, boolean isToolbar) {
        for (AnAction value : mIdToAction.values()) {
            AnActionEvent event = new AnActionEvent(place,
                    value.getTemplatePresentation(),
                    isContext,
                    isToolbar);
            fillMenu(menu, value, event);
        }
    }

    private void fillMenu(Menu menu, AnAction action, AnActionEvent event) {
        String id = getId(action);
        assert id != null;

        Presentation presentation = event.getPresentation();

        MenuItem menuItem;
        if (isGroup(id)) {
            SubMenu subMenu = menu.addSubMenu(presentation.getText());
            menuItem = subMenu.getItem();

            ActionGroup actionGroup = (ActionGroup) action;
            AnAction[] children = actionGroup.getChildren(event);
            if (children != null) {
                for (AnAction child : children) {
                    AnActionEvent childEvent = new AnActionEvent(event.getPlace(),
                            child.getTemplatePresentation(),
                            event.isContextMenuAction(),
                            event.isActionToolbar());
                    child.update(childEvent);
                    fillSubMenu(subMenu, child, event);
                }
            }
        } else {
            menuItem = menu.add(presentation.getText());
        }

        menuItem.setEnabled(presentation.isEnabled());
        menuItem.setVisible(presentation.isVisible());
        menuItem.setOnMenuItemClickListener(item -> {
            action.actionPerformed(event);
            return true;
        });
    }

    private void fillSubMenu(SubMenu subMenu, AnAction action, AnActionEvent event) {
        String id = getId(action);
        assert id != null;

        Presentation presentation = action.getTemplatePresentation();
        MenuItem menuItem;
        if (isGroup(id)) {
            ActionGroup group = (ActionGroup) action;
            SubMenu subSubMenu = subMenu.addSubMenu(presentation.getText());
            menuItem = subSubMenu.getItem();

            AnAction[] children = group.getChildren(event);
            if (children != null) {
                for (AnAction child : children) {
                    AnActionEvent childEvent = new AnActionEvent(event.getPlace(),
                            child.getTemplatePresentation(),
                            event.isContextMenuAction(),
                            event.isActionToolbar());
                    child.update(childEvent);
                    fillSubMenu(subSubMenu, child, childEvent);
                }
            }
        } else {
            menuItem = subMenu.add(presentation.getText());
        }

        menuItem.setEnabled(presentation.isEnabled());
        menuItem.setVisible(presentation.isVisible());
        menuItem.setOnMenuItemClickListener(item -> {
            action.actionPerformed(event);
            return true;
        });
    }

    @Override
    public String getId(@NonNull AnAction action) {
        return mActionToId.get(action);
    }

    @Override
    public void registerAction(@NonNull String actionId, @NonNull AnAction action) {
        mIdToAction.put(actionId, action);
        mActionToId.put(action, actionId);
    }

    @Override
    public void unregisterAction(@NonNull String actionId) {
        AnAction anAction = mIdToAction.get(actionId);
        if (anAction != null) {
            mIdToAction.remove(actionId);
            mActionToId.remove(anAction);
        }
    }

    @Override
    public void replaceAction(@NonNull String actionId, @NonNull AnAction newAction) {
        unregisterAction(actionId);
        registerAction(actionId, newAction);
    }

    @Override
    public boolean isGroup(@NonNull String actionId) {
        return mIdToAction.get(actionId) instanceof ActionGroup;
    }
}