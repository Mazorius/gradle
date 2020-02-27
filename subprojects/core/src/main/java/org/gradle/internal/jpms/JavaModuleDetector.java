/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.jpms;

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileCollection;
import org.gradle.api.jpms.ModularClasspathHandling;
import org.gradle.api.specs.Spec;
import org.gradle.cache.internal.FileContentCacheFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

public class JavaModuleDetector {

    private static final Spec<? super File> CLASSPATH_FILTER = (Spec<File>) JavaModuleDetector::isNotModule;
    private static final Spec<? super File> MODULE_PATH_FILTER = (Spec<File>) JavaModuleDetector::isModule;

    private static final String MODULE_INFO_SOURCE_FILE = "module-info.java";
    private static final String MODULE_INFO_CLASS_FILE = "module-info.class";
    private static final String AUTOMATIC_MODULE_NAME_ATTRIBUTE = "Automatic-Module-Name";
    //private final FileContentCache<List<JavaModuleDetector>> cache;

    public JavaModuleDetector(FileContentCacheFactory cacheFactory) {
        //this.cache = cacheFactory.newCache("annotation-processors", 20000, new ProcessorServiceLocator(), new ListSerializer<>(INSTANCE));
    }

    public ImmutableList<File> inferClasspath(boolean forModule, ModularClasspathHandling modularClasspathHandling, FileCollection classpath) {
        if (classpath == null) {
            return ImmutableList.of();
        }
        if (!forModule) {
            return ImmutableList.copyOf(classpath);
        }
        if (modularClasspathHandling.getInferModulePath()) {
            return ImmutableList.copyOf(classpath.filter(CLASSPATH_FILTER));
        }
        return ImmutableList.copyOf(classpath);
    }

    public ImmutableList<File> inferModulePath(boolean forModule, ModularClasspathHandling modularClasspathHandling, FileCollection classpath) {
        if (classpath == null) {
            return ImmutableList.of();
        }
        if (!forModule) {
            return ImmutableList.of();
        }
        if (modularClasspathHandling.getInferModulePath()) {
            return ImmutableList.copyOf(classpath.filter(MODULE_PATH_FILTER));
        }
        return ImmutableList.of();
    }

    public static boolean isModuleSource(Iterable<File> sourcesRoots) {
        for(File srcFolder : sourcesRoots) {
            if (isModuleSourceFolder(srcFolder)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isModule(File file) {
        if (!file.exists()) {
            // TODO this filters empty resource folders; what to do with non-empty ones?
            return false;
        }
        if (isJarFile(file)) {
            return isModuleJar(file);
        }
        if (isClassFolder(file)) {
            return isModuleFolder(file);
        }
        return false;
    }

    private static boolean isNotModule(File file) {
        if (!file.exists()) {
            return false;
        }
        return !isModule(file);
    }

    private static boolean isJarFile(File file) {
        return file.isFile() && file.getName().endsWith(".jar");
    }

    private static boolean isClassFolder(File file) {
        return file.isDirectory();
    }

    private static boolean isModuleFolder(File folder) {
        return new File(folder, MODULE_INFO_CLASS_FILE).exists();
    }

    private static boolean isModuleSourceFolder(File folder) {
        return new File(folder, MODULE_INFO_SOURCE_FILE).exists();
    }

    private static boolean isModuleJar(File jarFile) {
        try (JarInputStream jarStream =  new JarInputStream(new FileInputStream(jarFile))) {
            if (containsAutomaticModuleName(jarStream)) {
                return true;
            }
            ZipEntry next = jarStream.getNextEntry();
            while (next != null) {
                if (next.getName().equals(MODULE_INFO_CLASS_FILE)) {
                    return true;
                }
                next = jarStream.getNextEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private static boolean containsAutomaticModuleName(JarInputStream jarStream) {
        return getAutomaticModuleName(jarStream) != null;
    }

    private static String getAutomaticModuleName(JarInputStream jarStream) {
        return jarStream.getManifest().getMainAttributes().getValue(AUTOMATIC_MODULE_NAME_ATTRIBUTE);
    }
}
