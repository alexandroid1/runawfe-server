/*
 * This file is part of the RUNA WFE project.
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation; version 2.1 
 * of the License. 
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the 
 * GNU Lesser General Public License for more details. 
 * 
 * You should have received a copy of the GNU Lesser General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */
package ru.runa.wfe.presentation.hibernate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.runa.wfe.InternalApplicationException;
import ru.runa.wfe.presentation.BatchPresentation;
import ru.runa.wfe.presentation.ClassPresentation;
import ru.runa.wfe.presentation.FieldDescriptor;

/**
 * Holds mapping from {@link FieldDescriptor} to alias, assigned to field in HQL query and vice verse.
 */
public class HibernateCompilerAliasMapping {

    /**
     * Map {@link FieldDescriptor} to HQL query alias.
     */
    private final Map<FieldDescriptor, String> fieldToAlias = new HashMap<FieldDescriptor, String>();

    /**
     * Map HQL query alias to {@link FieldDescriptor}.
     */
    private final Map<String, List<FieldDescriptor>> aliasToField = new HashMap<String, List<FieldDescriptor>>();

    /**
     * Map Class to query alias.
     */
    private final Map<Class<?>, String> joinedClassToAlias = new HashMap<Class<?>, String>();

    /**
     * Map HQL query alias to class.
     */
    private final Map<String, Class<?>> joinedAliasToClass = new HashMap<String, Class<?>>();

    /**
     * Map visible Class to query alias.
     */
    private final Map<Class<?>, String> visibleJoinedClassToAlias = new HashMap<Class<?>, String>();
    /**
     * Map visible HQL query alias to class.
     */
    private final Map<String, Class<?>> visibleJoinedAliasToClass = new HashMap<String, Class<?>>();

    /**
     * Creates mapping from {@link FieldDescriptor} to alias for specified {@link BatchPresentation}.
     * 
     * @param batchPresentation
     *            {@link BatchPresentation} to initialize alias mappings.
     */
    public HibernateCompilerAliasMapping(BatchPresentation batchPresentation) {
        final List<Class<?>> displayEntities = new ArrayList<Class<?>>();
        for (final FieldDescriptor dysplayField : batchPresentation.getDisplayFields()) {
            final Class<?> dysplayEntity = dysplayField.dbSources[0].getSourceObject();
            if (!displayEntities.contains(dysplayEntity)) {
                displayEntities.add(dysplayEntity);
            }
        }

        FieldDescriptor[] fields = batchPresentation.getAllFields();
        int tableIndex = 0;
        for (int idx = 0; idx < fields.length; ++idx) {
            FieldDescriptor field = fields[idx];
            if (field.dbSources == null) {
                throw new InternalApplicationException("Field dbSource is null. Something wrong with " + batchPresentation);
            }
            final Class<?> entity = field.dbSources[0].getSourceObject();
            if (entity.equals(batchPresentation.getClassPresentation().getPresentationClass())) {
                addAliasMapping(field, ClassPresentation.classNameSQL);
            } else if (field.displayName.startsWith(ClassPresentation.removable_prefix)) {
                addAliasMapping(field, "editedField" + idx);
            } else if (field.displayName.startsWith(ClassPresentation.editable_prefix)) {
                ;
            } else {
                if (!joinedClassToAlias.containsKey(entity)) {
                    final String aliasName = "tbl" + ++tableIndex;
                    addJoinedAliasMapping(entity, aliasName);
                    if (displayEntities.contains(entity) && !visibleJoinedClassToAlias.containsKey(entity)) {
                        addVisibleJoinedAliasMapping(entity, aliasName);
                    }
                }
                addAliasMapping(field, joinedClassToAlias.get(entity));
            }
        }
    }

    /**
     * Return's HQL query alias for given field.
     * 
     * @param field
     *            Field, to get HQL alias.
     * @return HQL query alias.
     */
    public String getAlias(FieldDescriptor field) {
        return fieldToAlias.get(field);
    }

    /**
     * Returns fields, corresponds to HQL alias.
     * 
     * @param alias
     *            HQL query alias.
     * @return Field for HQL alias.
     */
    public List<FieldDescriptor> getFields(String alias) {
        if (!aliasToField.containsKey(alias)) {
            aliasToField.put(alias, new ArrayList<FieldDescriptor>());
        }
        return aliasToField.get(alias);
    }

    /**
     * Returns all HQL aliases, created for {@link BatchPresentation}.
     * 
     * @return All HQL aliases.
     */
    public Set<String> getAliases() {
        return aliasToField.keySet();
    }

    /**
     * Returns all fields in {@link BatchPresentation}.
     * 
     * @return All {@link BatchPresentation} fields.
     */
    public Set<FieldDescriptor> getFields() {
        return fieldToAlias.keySet();
    }

    /**
     * Returns joined entities in {@link BatchPresentation}.
     * 
     * @return All {@link BatchPresentation} entities.
     */
    public Set<Class<?>> getJoinedClasses() {
        return joinedClassToAlias.keySet();
    }

    /**
     * Returns visible joined entities in {@link BatchPresentation}.
     * 
     * @return All {@link BatchPresentation} entities.
     */
    public Set<Class<?>> getVisibleJoinedClasses() {
        return visibleJoinedClassToAlias.keySet();
    }

    /**
     * Returns joined aliases in {@link BatchPresentation}.
     * 
     * @return All {@link BatchPresentation} entities.
     */
    public Set<String> getJoinedAliases() {
        return joinedAliasToClass.keySet();
    }

    /**
     * Returns visible joined aliases in {@link BatchPresentation}.
     * 
     * @return All {@link BatchPresentation} entities.
     */
    public Set<String> getVisibleJoinedAliases() {
        return visibleJoinedAliasToClass.keySet();
    }

    /**
     * Add field and alias to corresponding map's
     * 
     * @param field
     *            Field, to add.
     * @param alias
     *            Alias for field.
     */
    private void addAliasMapping(FieldDescriptor field, String alias) {
        fieldToAlias.put(field, alias);
        final List<FieldDescriptor> fields = getFields(alias);
        fields.add(field);
        aliasToField.put(alias, fields);
    }

    /**
     * Add entity and alias to corresponding map's
     * 
     * @param entity
     *            Entity, to add.
     * @param alias
     *            Alias for field.
     */
    private void addJoinedAliasMapping(Class<?> entity, String alias) {
        joinedClassToAlias.put(entity, alias);
        joinedAliasToClass.put(alias, entity);
    }

    private void addVisibleJoinedAliasMapping(Class<?> entity, String alias) {
        visibleJoinedClassToAlias.put(entity, alias);
        visibleJoinedAliasToClass.put(alias, entity);
    }
}
