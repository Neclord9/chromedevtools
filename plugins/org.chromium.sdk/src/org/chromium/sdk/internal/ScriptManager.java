// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.sdk.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.chromium.sdk.Script;
import org.chromium.sdk.internal.ScriptImpl.Descriptor;
import org.chromium.sdk.internal.tools.v8.V8Protocol;
import org.chromium.sdk.internal.tools.v8.V8ProtocolUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Manages scripts known in the corresponding browser tab.
 */
public class ScriptManager {

  public interface Callback {
    /**
     * This method gets invoked for every script in the manager.
     *
     * @param script to process
     * @return whether other scripts should be processed. If false, the #forEach
     *         method terminates.
     */
    boolean process(Script script);
  }

  /**
   * Maps script id's to scripts.
   */
  private final Map<Long, ScriptImpl> idToScript =
      Collections.synchronizedMap(new HashMap<Long, ScriptImpl>());

  /**
   * Adds a script using a "script" V8 response.
   *
   * @param scriptBody to add the script from
   * @param refs that contain the associated script debug context
   * @return the new script, or null if the response does not contain a script
   *         name
   */
  public Script addScript(JSONObject scriptBody, JSONArray refs) {

    ScriptImpl theScript = findById(V8ProtocolUtil.getScriptIdFromResponse(scriptBody));

    if (theScript == null) {
      Descriptor desc = Descriptor.forResponse(scriptBody, refs);
      if (desc == null) {
        return null;
      }
      theScript = new ScriptImpl(desc);
      idToScript.put(desc.id, theScript);
    }
    if (scriptBody.containsKey(V8Protocol.SOURCE_CODE.key)) {
      setSourceCode(scriptBody, theScript);
    }

    return theScript;
  }

  /**
   * Tells whether a script specified by the {@code response} is known to this
   * manager.
   *
   * @param response containing the script to check
   * @return whether the script is known to this manager. Will also return
   *         {@code false} if the script name is absent in the {@code response}
   */
  public boolean hasScript(JSONObject response) {
    return findById(V8ProtocolUtil.getScriptIdFromResponse(response)) != null;
  }

  /**
   * Associates a source received in a "source" V8 response with the given
   * script.
   *
   * @param body the JSON response body
   * @param script the script to associate the source with
   */
  public void setSourceCode(JSONObject body, ScriptImpl script) {
    String src = JsonUtil.getAsString(body, V8Protocol.SOURCE_CODE);
    if (src == null) {
      return;
    }
    if (script != null) {
      script.setSource(src);
    }
  }

  /**
   * @param id of the script to find
   * @return the script with {@code id == ref} or {@code null} if none found
   */
  public ScriptImpl findById(Long id) {
    return idToScript.get(id);
  }

  /**
   * Determines whether all scripts added into this manager have associated
   * sources.
   *
   * @return whether all known scripts have associated sources
   */
  public boolean isAllSourcesLoaded() {
    final boolean[] result = new boolean[1];
    result[0] = true;
    forEach(new Callback() {
      public boolean process(Script script) {
        if (!script.hasSource()) {
          result[0] = false;
          return false;
        }
        return true;
      }
    });
    return result[0];
  }

  public Collection<Script> allScripts() {
    final Collection<Script> result = new HashSet<Script>();
    forEach(new Callback() {
      public boolean process(Script script) {
        result.add(script);
        return true;
      }
    });
    return result;
  }

  /**
   * This method allows running the same code for all scripts in the manager.
   *
   * @param callback to invoke for every script, until
   *        {@link Callback#process(Script)} returns {@code false}.
   */
  public void forEach(Callback callback) {
    for (Script script : idToScript.values()) {
      if (!callback.process(script)) {
        return;
      }
    }
  }

  public void reset() {
    idToScript.clear();
  }

}