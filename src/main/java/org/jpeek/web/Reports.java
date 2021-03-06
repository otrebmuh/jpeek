/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2019 Yegor Bugayenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.jpeek.web;

import com.jcabi.xml.XMLDocument;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.cactoos.BiFunc;
import org.cactoos.Func;
import org.cactoos.io.TeeInput;
import org.cactoos.scalar.IoChecked;
import org.cactoos.scalar.LengthOf;
import org.cactoos.text.TextOf;
import org.jpeek.App;
import org.takes.Response;

/**
 * All reports.
 *
 * <p>There is NO thread-safety guarantee. Moreover, this class is NOT
 * thread-safe. You have to decorate it with a thread-safe
 * {@link Futures}.
 *
 * @author Yegor Bugayenko (yegor256@gmail.com)
 * @version $Id$
 * @since 0.7
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle JavadocTagsCheck (500 lines)
 */
final class Reports implements BiFunc<String, String, Func<String, Response>> {

    /**
     * Directory with sources.
     */
    private final Path sources;

    /**
     * Directory with reports.
     */
    private final Path target;

    /**
     * Ctor.
     * @param home Home dir
     */
    Reports(final Path home) {
        this(home.resolve("sources"), home.resolve("target"));
    }

    /**
     * Ctor.
     * @param input Dir with sources
     * @param output Dir with reports
     */
    Reports(final Path input, final Path output) {
        this.sources = input;
        this.target = output;
    }

    // @checkstyle ExecutableStatementCountCheck (100 lines)
    @SuppressWarnings("PMD.CyclomaticComplexity")
    @Override
    public Func<String, Response> apply(final String group,
        final String artifact) throws IOException {
        final String grp = group.replace(".", "/");
        final Path input = this.sources.resolve(grp).resolve(artifact);
        Reports.deleteIfPresent(input);
        final String version = new XMLDocument(
            new TextOf(
                new URL(
                    String.format(
                        // @checkstyle LineLength (1 line)
                        "http://repo1.maven.org/maven2/%s/%s/maven-metadata.xml",
                        grp, artifact
                    )
                )
            ).asString()
        ).xpath("/metadata/versioning/latest/text()").get(0);
        final String name = String.format("%s-%s.jar", artifact, version);
        new IoChecked<>(
            new LengthOf(
                new TeeInput(
                    new URL(
                        String.format(
                            "http://repo1.maven.org/maven2/%s/%s/%s/%s",
                            grp, artifact, version, name
                        )
                    ),
                    input.resolve(name)
                )
            )
        ).value();
        try {
            final File none;
            final String filepath = "/dev/null";
            if (new File(filepath).exists()) {
                none = new File(filepath);
            } else {
                none = new File("NUL");
            }
            final int exit = new ProcessBuilder()
                .redirectOutput(ProcessBuilder.Redirect.to(none))
                .redirectInput(ProcessBuilder.Redirect.from(none))
                .redirectError(ProcessBuilder.Redirect.to(none))
                .directory(input.toFile())
                .command("unzip", name)
                .start()
                .waitFor();
            if (exit != 0) {
                throw new IllegalStateException(
                    String.format(
                        "Failed to unzip %s:%s archive, exit code %d",
                        group, artifact, exit
                    )
                );
            }
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
        final Path output = this.target.resolve(grp).resolve(artifact);
        Reports.deleteIfPresent(output);
        new App(input, output).analyze();
        synchronized (this.sources) {
            new Results().add(String.format("%s:%s", group, artifact), output);
            new Mistakes().add(output);
            new Sigmas().add(output);
        }
        return new TypedPages(new Pages(output));
    }

    /**
     * Delete this dir if it's present.
     * @param dir The dir
     * @throws IOException If fails
     */
    private static void deleteIfPresent(final Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

}
