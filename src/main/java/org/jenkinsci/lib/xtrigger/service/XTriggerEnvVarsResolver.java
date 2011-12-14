package org.jenkinsci.lib.xtrigger.service;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Plugin;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Run;
import hudson.remoting.Callable;
import org.jenkinsci.lib.xtrigger.XTriggerException;
import org.jenkinsci.lib.xtrigger.XTriggerLog;
import org.jenkinsci.plugins.envinject.EnvInjectAction;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

/**
 * @author Gregory Boissinot
 */
public class XTriggerEnvVarsResolver implements Serializable {

    public Map<String, String> getEnvVars(AbstractProject project, Node node, XTriggerLog log) throws XTriggerException {
        Run lastBuild = project.getLastBuild();
        if (lastBuild != null) {
            if (isEnvInjectPluginActivated()) {
                EnvInjectAction envInjectAction = lastBuild.getAction(EnvInjectAction.class);
                if (envInjectAction != null) {
                    return envInjectAction.getEnvMap();
                }
            }
        }
        return getDefaultEnvVarsJob(project, node);
    }

    private boolean isEnvInjectPluginActivated() {
        Plugin envInjectPlugin = Hudson.getInstance().getPlugin("envinject");
        return envInjectPlugin != null;
    }

    private Map<String, String> getDefaultEnvVarsJob(AbstractProject project, Node node) throws XTriggerException {
        Map<String, String> result = computeEnvVarsMaster(project);
        if (node != null) {
            result.putAll(computeEnvVarsNode(project, node));
        }
        return result;
    }

    private Map<String, String> computeEnvVarsMaster(AbstractProject project) throws XTriggerException {
        EnvVars env = new EnvVars();
        env.put("JENKINS_SERVER_COOKIE", Util.getDigestOf("ServerID:" + Hudson.getInstance().getSecretKey()));
        env.put("HUDSON_SERVER_COOKIE", Util.getDigestOf("ServerID:" + Hudson.getInstance().getSecretKey())); // Legacy compatibility
        env.put("JOB_NAME", project.getFullName());
        env.put("JENKINS_HOME", Hudson.getInstance().getRootDir().getPath());
        env.put("HUDSON_HOME", Hudson.getInstance().getRootDir().getPath());   // legacy compatibility
        return env;
    }

    private Map<String, String> computeEnvVarsNode(AbstractProject project, Node node) throws XTriggerException {
        assert node != null;
        assert node.getRootPath() != null;
        try {
            Map<String, String> envVars = node.getRootPath().act(new Callable<Map<String, String>, XTriggerException>() {
                public Map<String, String> call() throws XTriggerException {
                    return EnvVars.masterEnvVars;
                }
            });

            envVars.put("NODE_NAME", node.getNodeName());
            envVars.put("NODE_LABELS", Util.join(node.getAssignedLabels(), " "));
            FilePath wFilePath = project.getSomeWorkspace();
            if (wFilePath != null) {
                envVars.put("WORKSPACE", wFilePath.getRemote());
            }

            return envVars;

        } catch (IOException ioe) {
            throw new XTriggerException(ioe);
        } catch (InterruptedException ie) {
            throw new XTriggerException(ie);
        }
    }

}

