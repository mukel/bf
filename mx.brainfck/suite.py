#
# Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
suite = {
    "mxversion": "5.273.10",
    "name": "brainfck",

    # ------------- licenses

    "licenses": {
        "GPLv2": {
            "name": "GNU General Public License, version 2",
            "url": "http://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
        },
    },
    "defaultLicense": "GPLv2",

    # ------------- imports

    "imports": {
        "suites": [
            {
                "name": "truffle",
                "subdir": True,
                # Custom changes in Truffle (NFI) for Espresso (branch slimbeans).
                "version": "a145eed23fd5026c9cea83b77604a8aff6b58432",
                "urls": [
                    {"url": "https://github.com/graalvm/graal", "kind": "git"},
                    {"url": "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind": "binary"},
                ]
            },            
        ],
    },

    # ------------- projects

    "projects": {

	    "com.oracle.truffle.brainfck": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
            ],
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "javaCompliance": "1.8+",
            "checkstyle": "com.oracle.truffle.brainfck",
            "checkstyleVersion": "8.8",            
        },

        
        "com.oracle.truffle.brainfck.launcher": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "javaCompliance": "1.8+",
            "checkstyle": "com.oracle.truffle.brainfck",
        },

        "com.oracle.truffle.brainfck.test": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "testProject": True,
            "dependencies": [
                "com.oracle.truffle.brainfck",
                "mx:JUNIT",
                "mx:ASM_COMMONS_7.1",
            ],
            "javaCompliance": "1.8+",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "checkstyle": "com.oracle.truffle.brainfck",
        },
    },

    # ------------- distributions

    "distributions": {

        "BRAINFCK": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.brainfck",
            ],
            "distDependencies": [
                "truffle:TRUFFLE_API",
            ],
            "javaProperties": {
                "org.graalvm.language.java.home": "<path:BRAINFCK_SUPPORT>",
            },
        },

        "BRAINFCK_TESTS": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.brainfck.test"
            ],
            "distDependencies": [
                "brainfck:BRAINFCK",                
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_TCK",
                "mx:JUNIT",
            ],            
            "testDistribution": True,
        },
        
        "BRAINFCK_LAUNCHER": {
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.brainfck.launcher",
            ],
            "mainClass": "com.oracle.truffle.brainfck.launcher.BrainfckLauncher",
            "distDependencies": [
                "sdk:GRAAL_SDK",
                "sdk:LAUNCHER_COMMON",
            ],
            "license": "UPL",
            "description": "Brainfck launcher using the polyglot API.",
            "allowsJavadocWarnings": True,
        },

        "BRAINFCK_SUPPORT": {
            "native": True,
            "description": "Brainfck support distribution for the GraalVM",
            "platformDependent": True,
            "layout": {
                "./": [
                    "file:mx.brainfck/native-image.properties",
                    "file:mx.brainfck/reflectconfig.json",
                ],
            },
        },
    }
}
