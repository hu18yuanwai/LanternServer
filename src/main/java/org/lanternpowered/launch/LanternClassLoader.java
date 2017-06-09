/*
 * This file is part of LanternServer, licensed under the MIT License (MIT).
 *
 * Copyright (c) LanternPowered <https://www.lanternpowered.org>
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the Software), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED AS IS, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.lanternpowered.launch;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.TERMINATE;
import static java.util.Objects.requireNonNull;

import org.lanternpowered.launch.transformer.ClassTransformer;
import org.lanternpowered.launch.transformer.ClassTransformers;
import org.lanternpowered.launch.transformer.Exclusion;
import org.lanternpowered.server.LanternServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A {@link ClassLoader} that gives complete control over all the libraries used by
 * {@link LanternServer}. Mainly designed for Java 9+, the System ClassLoader no longer
 * extends {@link URLClassLoader}. This {@link ClassLoader} should be used instead.
 * <p>
 * All {@link Class#forName(String)} operations will be delegated through this
 * {@link ClassLoader}.
 */
public final class LanternClassLoader extends URLClassLoader {

    private static final LanternClassLoader classLoader;

    static {
        ClassLoader.registerAsParallelCapable();

        // All the folders are from lantern or sponge,
        // in development mode are all the libraries on
        // the classpath, so there is no need to add them
        // to the library classloader
        final List<URL> urls = new ArrayList<>();

        // If we are outside development mode, the server will be packed
        // into a jar. We will also need to make sure that this one gets
        // added in this case
        final CodeSource source = LanternClassLoader.class.getProtectionDomain().getCodeSource();
        final URL location = source == null ? null : source.getLocation();

        final String classPath = System.getProperty("java.class.path");
        final String[] libraries = classPath.split(File.pathSeparator);
        for (String library : libraries) {
            try {
                final URL url = Paths.get(library).toUri().toURL();
                if (!library.endsWith(".jar") || url.equals(location)) {
                    urls.add(url);
                }
            } catch (MalformedURLException ignored) {
                System.out.println("Invalid library found in the class path: " + library);
            }
        }

        final ClassLoader parent = LanternClassLoader.class.getClassLoader();
        final List<URL> libraryUrls = new ArrayList<>();
        final List<String> libraryNames = new ArrayList<>();

        // First cleanup old libraries
        final Path internalLibrariesPath = Paths.get(".internal-libraries");
        if (Files.exists(internalLibrariesPath)) {
            try {
                Files.walkFileTree(internalLibrariesPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) {
                        e.printStackTrace();
                        return TERMINATE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                        if (e != null) {
                            e.printStackTrace();
                            return TERMINATE;
                        }
                        Files.delete(dir);
                        return CONTINUE;
                    }
                });
            } catch (IOException e) {
                System.out.println("Failed to cleanup the internal libraries: " + e);
            }
        }
        // Scan the jar for library jars
        if (location != null) {
            try (ZipInputStream is = new ZipInputStream(location.openStream())) {
                ZipEntry e;
                while ((e = is.getNextEntry()) != null) {
                    final String name = e.getName();
                    // Check if it's a library jar
                    if (name.startsWith("libraries") && name.endsWith(".jar")) {
                        // Yay
                        final String n = name.substring("libraries/".length());
                        final URL url = parent.getResource(name);
                        requireNonNull(url, "Something funky happened");
                        final Path path = internalLibrariesPath.resolve(n);
                        final Path p = path.getParent();
                        if (!Files.exists(p)) {
                            Files.createDirectories(p);
                            if (Files.exists(path)) {
                                Files.delete(path);
                            }
                        }

                        try (ReadableByteChannel i = Channels.newChannel(url.openStream());
                                FileOutputStream o = new FileOutputStream(path.toFile())) {
                            o.getChannel().transferFrom(i, 0, Long.MAX_VALUE);
                        }

                        libraryUrls.add(path.toUri().toURL());
                        libraryNames.add(n);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if ("true".equalsIgnoreCase(System.getProperty("log-loaded-libraries"))) {
            // Sort the library names, no mess, or at least try to reduce it
            Collections.sort(libraryNames);
            libraryNames.forEach(name -> System.out.println("Loaded library: " + name));
        }

        // The server class loader will load lantern, the api and all the plugins
        final LanternClassLoader serverClassLoader = new LanternClassLoader(
                urls.toArray(new URL[urls.size()]), libraryUrls.toArray(new URL[libraryUrls.size()]), parent);

        classLoader = serverClassLoader;
        Thread.currentThread().setContextClassLoader(serverClassLoader);
    }

    /**
     * Gets the {@link LanternClassLoader}.
     *
     * @return The class loader
     */
    public static LanternClassLoader get() {
        return classLoader;
    }

    private static final int BUFFER_SIZE = 1 << 12;

    private final Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
    private final Set<String> invalidClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ThreadLocal<byte[]> loadBuffer = ThreadLocal.withInitial(() -> new byte[BUFFER_SIZE]);

    // A classloader that will be used to load library class files
    private final LibraryClassLoader libraryClassLoader;
    private final Set<URL> libraryUrls = new HashSet<>();
    private final Set<URL> urls = new HashSet<>();

    private static final class LibraryClassLoader extends URLClassLoader {
        private LibraryClassLoader(URL[] urls) {
            super(urls);
        }
        @Override
        protected void addURL(URL url) {
            super.addURL(url);
        }
    }

    private LanternClassLoader(URL[] urls, URL[] libraryUrls, ClassLoader parent) {
        super(urls, parent);
        this.urls.addAll(Arrays.asList(urls));
        this.libraryUrls.addAll(Arrays.asList(libraryUrls));
        this.libraryClassLoader = new LibraryClassLoader(libraryUrls);
    }

    /**
     * The same as {@link Class#forName(String, boolean, ClassLoader)},
     * but called for this {@link ClassLoader}.
     *
     * @see Class#forName(String, boolean, ClassLoader)
     */
    public Class<?> forName(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, this);
    }

    /**
     * Adds a library {@link URL}. All the library classes
     * will be ignored by {@link ClassTransformers}.
     *
     * @param url The url
     */
    public void addLibraryURL(URL url) {
        requireNonNull(url, "url");
        // Make sure that there can't be duplicate libraries
        if (this.libraryUrls.add(url)) {
            this.libraryClassLoader.addURL(url);
            // New classes are available, let the class loader try again
            this.invalidClasses.clear();
        }
    }

    @Override
    public void addURL(URL url) {
        requireNonNull(url, "url");
        // Make sure that there can't be duplicate jars
        if (this.urls.add(url)) {
            super.addURL(url);
            // New classes are available, let the class loader try again
            this.invalidClasses.clear();
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                // Ignore the launch package, this is the only package that will be loaded
                // through the system class loader
                if (name.startsWith("org.lanternpowered.launch.")) {
                    // This has to be found
                    c = getParent().loadClass(name);
                } else {
                    ClassNotFoundException e = null;
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException ex) {
                        e = ex;
                    }
                    if (c == null) {
                        try {
                            c = getParent().loadClass(name);
                        } catch (ClassNotFoundException ex) {
                            // Throw the error generated by this class loader,
                            // it might be more useful
                            throw e;
                        }
                    }
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // The class loading failed before
        if (this.invalidClasses.contains(name)) {
            throw new ClassNotFoundException(name);
        }
        if (this.cachedClasses.containsKey(name)) {
            return this.cachedClasses.get(name);
        }
        final String fileName = name.replace('.', '/').concat(".class");
        // Try the server classes
        URL resource = findResource(fileName);
        if (resource == null) {
            // Try library classes
            resource = this.libraryClassLoader.findResource(fileName);
            if (resource == null) {
                this.invalidClasses.add(name);
                throw new ClassNotFoundException(name);
            }
            // Just load the library class
            return defineClass(name, resource);
        }
        final ClassTransformers transformers = ClassTransformers.get();
        if (transformers.getTransformers().isEmpty()) {
            // Don't bother if there are no transformers
            return defineClass(name, resource);
        }
        // Check if the class should be ignored by any kind of transformer
        for (Exclusion exclusion : transformers.getExclusions()) {
            if (exclusion.isApplicableFor(name)) {
                // Just load the class in this case
                return defineClass(name, resource);
            }
        }
        try (InputStream is = resource.openStream()) {
            definePackage(name);

            // Get the buffer
            byte[] buffer = this.loadBuffer.get();

            int read;
            int totalLength = 0;
            while ((read = is.read(buffer, totalLength, buffer.length - totalLength)) != -1) {
                totalLength += read;

                // Expand the buffer
                if (totalLength >= buffer.length - 1) {
                    final byte[] newBuffer = new byte[buffer.length + BUFFER_SIZE];
                    System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
                    buffer = newBuffer;
                }
            }

            // Write the bytes to a byte array with the proper length,
            // we don't want any trailing bytes when pushing the byte
            // array through the transformers
            byte[] result = new byte[totalLength];
            System.arraycopy(buffer, 0, result, 0, totalLength);

            // Let's start transforming the class
            for (ClassTransformer transformer : transformers.getTransformers()) {
                try {
                    result = transformer.transform(this, name, result);
                } catch (Exception e) {
                    System.err.println("An error occurred while transforming " + name + ": " + e);
                }
            }

            final Class<?> clazz = defineClass(name, result, 0, result.length);
            this.cachedClasses.put(name, clazz);
            return clazz;
        } catch (Throwable e) {
            this.invalidClasses.add(name);
            throw new ClassNotFoundException(name, e);
        }
    }

    private void definePackage(String name) {
        final int lastDot = name.lastIndexOf('.');
        final String packageName = lastDot == -1 ? "" : name.substring(0, lastDot);

        final Package pkg = getPackage(packageName);
        if (pkg == null) {
            definePackage(packageName, null, null, null, null, null, null, null);
        }
    }

    private Class<?> defineClass(String name, URL url) throws ClassNotFoundException {
        definePackage(name);
        try (InputStream is = url.openStream()) {
            // Get the buffer
            byte[] buffer = this.loadBuffer.get();

            int read;
            int totalLength = 0;
            while ((read = is.read(buffer, totalLength, buffer.length - totalLength)) != -1) {
                totalLength += read;

                // Expand the buffer
                if (totalLength >= buffer.length - 1) {
                    final byte[] newBuffer = new byte[buffer.length + BUFFER_SIZE];
                    System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
                    buffer = newBuffer;
                }
            }

            final Class<?> clazz = defineClass(name, buffer, 0, totalLength);
            this.cachedClasses.put(name, clazz);
            return clazz;
        } catch (IOException e) {
            this.invalidClasses.add(name);
            throw new ClassNotFoundException(name, e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        final Enumeration<URL>[] enumerations = new Enumeration[2];
        enumerations[0] = super.findResources(name);
        enumerations[1] = this.libraryClassLoader.findResources(name);
        return new Enumeration<URL>() {

            private int index = 0;

            @Override
            public boolean hasMoreElements() {
                while (this.index < enumerations.length) {
                    if (enumerations[this.index].hasMoreElements()) {
                        return true;
                    }
                    this.index++;
                }
                return false;
            }

            @Override
            public URL nextElement() {
                if (hasMoreElements()) {
                    return enumerations[this.index].nextElement();
                }
                throw new NoSuchElementException();
            }
        };
    }

    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        if (url != null) {
            return url;
        }
        url = this.libraryClassLoader.findResource(name);
        if (url != null) {
            return url;
        }
        return getParent().getResource(name);
    }

    @Override
    public URL[] getURLs() {
        final Set<URL> urls = new HashSet<>();
        urls.addAll(Arrays.asList(super.getURLs()));
        urls.addAll(Arrays.asList(this.libraryClassLoader.getURLs()));
        return urls.toArray(new URL[urls.size()]);
    }
}
