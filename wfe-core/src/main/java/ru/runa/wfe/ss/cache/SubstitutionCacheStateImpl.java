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
package ru.runa.wfe.ss.cache;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ru.runa.wfe.commons.ApplicationContextFactory;
import ru.runa.wfe.commons.cache.BaseCacheImpl;
import ru.runa.wfe.commons.cache.Cache;
import ru.runa.wfe.commons.cache.CacheImplementation;
import ru.runa.wfe.commons.cache.ChangedObjectParameter;
import ru.runa.wfe.execution.logic.SwimlaneInitializerHelper;
import ru.runa.wfe.ss.Substitution;
import ru.runa.wfe.ss.TerminatorSubstitution;
import ru.runa.wfe.ss.dao.SubstitutionDAO;
import ru.runa.wfe.user.Actor;
import ru.runa.wfe.user.Executor;
import ru.runa.wfe.user.ExecutorDoesNotExistException;
import ru.runa.wfe.user.Group;
import ru.runa.wfe.user.dao.ExecutorDAO;

import com.google.common.collect.Maps;

class SubstitutionCacheStateImpl extends BaseCacheImpl implements ManageableSubstitutionCache {
    private static final Log log = LogFactory.getLog(SubstitutionCacheStateImpl.class);
    public static final String substitutorsName = "ru.runa.wfe.ss.cache.substitutors";
    public static final String substitutedName = "ru.runa.wfe.ss.cache.substituted";
    private final Cache<Long, TreeMap<Substitution, HashSet<Long>>> actorToSubstitutorsCache;
    private final Cache<Long, HashSet<Long>> actorToSubstitutedCache;
    private final ExecutorDAO executorDAO = ApplicationContextFactory.getExecutorDAO();
    private final SubstitutionDAO substitutionDAO = ApplicationContextFactory.getSubstitutionDAO();

    public SubstitutionCacheStateImpl() {
        actorToSubstitutorsCache = createCache(substitutorsName, true);
        actorToSubstitutedCache = createCache(substitutedName, true);
        Map<Long, TreeMap<Substitution, HashSet<Long>>> actorToSubstitutors = getMapActorToSubstitutors();
        Map<Long, HashSet<Long>> actorToSubstituted = getMapActorToSubstituted(actorToSubstitutors);
        actorToSubstitutorsCache.putAll(actorToSubstitutors);
        actorToSubstitutedCache.putAll(actorToSubstituted);
    }

    @Override
    public TreeMap<Substitution, Set<Long>> getSubstitutors(Actor actor, boolean loadIfRequired) {
        if (actor.isActive()) {
            return new TreeMap<Substitution, Set<Long>>();
        }
        TreeMap<Substitution, HashSet<Long>> result = actorToSubstitutorsCache.get(actor.getId());
        if (result != null) {
            return new TreeMap<Substitution, Set<Long>>(result);
        }
        return new TreeMap<Substitution, Set<Long>>();
    }

    @Override
    public TreeMap<Substitution, Set<Long>> tryToGetSubstitutors(Actor actor) {
        return null;
    }

    @Override
    public HashSet<Long> getSubstituted(Actor actor) {
        HashSet<Long> result = actorToSubstitutedCache.get(actor.getId());
        if (result != null) {
            return result;
        }
        return new HashSet<Long>();
    }

    private Map<Long, TreeMap<Substitution, HashSet<Long>>> getMapActorToSubstitutors() {
        Map<Long, TreeMap<Substitution, HashSet<Long>>> result = Maps.newHashMap();
        try {
            for (Substitution substitution : substitutionDAO.getAll()) {
                try {
                    Long actorId;
                    try {
                        actorId = executorDAO.getActor(substitution.getActorId()).getId();
                    } catch (ExecutorDoesNotExistException e) {
                        log.error("in " + substitution + ": " + e);
                        continue;
                    }
                    if (!substitution.isEnabled()) {
                        continue;
                    }
                    TreeMap<Substitution, HashSet<Long>> subDescr = result.get(actorId);
                    if (subDescr == null) {
                        subDescr = new TreeMap<Substitution, HashSet<Long>>();
                        result.put(actorId, subDescr);
                    }
                    if (substitution instanceof TerminatorSubstitution) {
                        subDescr.put(substitution, null);
                        continue;
                    }
                    List<? extends Executor> executors = SwimlaneInitializerHelper.evaluate(substitution.getOrgFunction(), null);
                    HashSet<Long> substitutors = new HashSet<Long>();
                    for (Executor sub : executors) {
                        if (sub instanceof Actor) {
                            substitutors.add(sub.getId());
                        } else {
                            for (Actor groupActor : executorDAO.getGroupActors((Group) sub)) {
                                substitutors.add(groupActor.getId());
                            }
                        }
                    }
                    subDescr.put(substitution, substitutors);
                } catch (Exception e) {
                    log.error("Error in " + substitution, e);
                }
            }
        } catch (Throwable th) {
            log.error("in substitution", th);
        }
        return result;
    }

    private Map<Long, HashSet<Long>> getMapActorToSubstituted(Map<Long, TreeMap<Substitution, HashSet<Long>>> mapActorToSubstitutors) {
        Map<Long, HashSet<Long>> result = new HashMap<Long, HashSet<Long>>();
        for (Long substitutedId : mapActorToSubstitutors.keySet()) {
            try {
                Actor substitutedActor = executorDAO.getActor(substitutedId);
                if (substitutedActor.isActive()) {
                    continue;
                }
                for (HashSet<Long> substitutors : mapActorToSubstitutors.get(substitutedId).values()) {
                    if (substitutors == null) {
                        continue;
                    }
                    for (Long substitutor : substitutors) {
                        HashSet<Long> set = result.get(substitutor);
                        if (set == null) {
                            set = new HashSet<Long>();
                            result.put(substitutor, set);
                        }
                        set.add(substitutedActor.getId());
                    }
                }
            } catch (ExecutorDoesNotExistException e) {
            }
        }
        return result;
    }

    @Override
    public CacheImplementation unlock() {
        return null;
    }

    @Override
    public boolean onChange(ChangedObjectParameter changedObject) {
        return false;
    }
}
