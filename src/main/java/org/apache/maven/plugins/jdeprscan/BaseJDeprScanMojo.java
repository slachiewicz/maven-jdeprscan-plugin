/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.jdeprscan;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.api.PathScope;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.DependencyResolver;
import org.apache.maven.api.services.DependencyResolverException;
import org.apache.maven.plugins.jdeprscan.consumers.JDeprScanConsumer;
import org.codehaus.plexus.util.cli.CommandLineUtils.StringStreamConsumer;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Base class for all explicit jdeprscan mojos.
 *
 * @author Robert Scholte
 * @since 3.0.0
 */
public abstract class BaseJDeprScanMojo extends AbstractJDeprScanMojo {
    @Inject
    private Project project;

    @Inject
    private Session session;

    @Inject
    private DependencyResolver dependencyResolver;

    /**
     * Indicates whether the build will continue even if there are jdeprscan warnings.
     */
    @Parameter(defaultValue = "true")
    private boolean failOnWarning;

    /**
     * Limits scanning or listing to APIs that are deprecated for removal.
     * Can’t be used with a release value of 6, 7, or 8.
     */
    @Parameter(property = "maven.jdeprscan.forremoval")
    private boolean forRemoval;

    /**
     * Specifies the Java SE release that provides the set of deprecated APIs for scanning.
     */
    @Parameter
    private String release;

    private final JDeprScanConsumer consumer = new JDeprScanConsumer();

    @Override
    public void execute() throws MojoException {
        if (!Files.exists(getClassesDirectory())) {
            getLog().debug("No classes to scan");
            return;
        }
        super.execute();
    }

    protected Project getProject() {
        return project;
    }

    /**
     * Resolves the dependencies of the project for the given scope, in the order Maven puts them on a classpath.
     */
    protected List<Path> resolveDependencies(PathScope scope) throws MojoException {
        try {
            return dependencyResolver.resolve(session, project, scope).getPaths();
        } catch (DependencyResolverException e) {
            throw new MojoException(e.getMessage(), e);
        }
    }

    @Override
    protected boolean isForRemoval() {
        return forRemoval;
    }

    @Override
    protected StringStreamConsumer getConsumer() {
        return consumer;
    }

    @Override
    protected final void addJDeprScanOptions(Commandline cmd) throws MojoException {
        super.addJDeprScanOptions(cmd);

        if (release != null) {
            cmd.createArg().setValue("--release");

            cmd.createArg().setValue(release);
        }

        Collection<Path> cp = getClassPath();

        if (!cp.isEmpty()) {
            cmd.createArg().setValue("--class-path");
            String classpath = cp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
            cmd.createArg().setValue(classpath);
        }

        cmd.createArg().setFile(getClassesDirectory().toFile());
    }

    @Override
    protected void verify() throws MojoException {
        if (!(consumer.getDeprecatedClasses().isEmpty()
                && consumer.getDeprecatedMethods().isEmpty())) {
            if (!consumer.getDeprecatedClasses().isEmpty()) {
                getLog().warn("Found usage of deprecated classes:");

                for (Map.Entry<String, Set<String>> classes :
                        consumer.getDeprecatedClasses().entrySet()) {
                    getLog().warn("class " + classes.getKey() + " uses deprecated class(es)");
                    for (String deprClass : classes.getValue()) {
                        getLog().warn("  * " + deprClass);
                    }
                }
            }

            if (!consumer.getDeprecatedMethods().isEmpty()) {
                getLog().warn("Found usage of deprecated methods:");

                for (Map.Entry<String, Set<String>> classes :
                        consumer.getDeprecatedMethods().entrySet()) {
                    getLog().warn("class " + classes.getKey() + " uses deprecated method(s)");
                    for (String deprMethod : classes.getValue()) {
                        getLog().warn("  * " + deprMethod);
                    }
                }
            }

            if (failOnWarning) {
                throw new MojoException("JDeprScan detected usage of deprecated classes/methods");
            }
        }
    }

    protected abstract Path getClassesDirectory();

    protected abstract Collection<Path> getClassPath() throws MojoException;
}
