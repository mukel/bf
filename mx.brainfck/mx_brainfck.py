#
# Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

from argparse import ArgumentParser

import os

import mx
import mx_sdk_vm
from mx_gate import Task, add_gate_runner
from mx_jackpot import jackpot
from mx_unittest import unittest


_suite = mx.suite('brainfck')


def _brainfck_launcher_command(args):
    """Brainf*ck launcher embedded in GraalVM + arguments"""
    import mx_sdk_vm_impl
    return [os.path.join(mx_sdk_vm_impl.graalvm_home(fatalIfMissing=True), 'bin', mx.cmd_suffix('bf'))] + args

def _brainfck_standalone_command(args):
    """Brainf*ck standalone command from distribution jars + arguments"""
    vm_args, args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    return (
        vm_args
        + mx.get_runtime_jvm_args(['BRAINFCK', 'BRAINFCK_LAUNCHER'], jdk=mx.get_jdk())
        + [mx.distribution('BRAINFCK_LAUNCHER').mainClass] + args
    )


def _run_brainfck_launcher(args=None, cwd=None):
    """Run Brainf*ck launcher within a GraalVM"""
    mx.run(_brainfck_launcher_command(args), cwd=cwd)


def _run_brainfck_standalone(args=None, cwd=None):
    """Run standalone Brainf*ck (not as part of GraalVM) from distribution jars"""
    mx.run_java(_brainfck_standalone_command(args), cwd=cwd)


class BrainfckDefaultTags:
    unittest = 'unittest'
    jackpot = 'jackpot'


def _brainfck_gate_runner(args, tasks):
    with Task('UnitTests', tasks, tags=[BrainfckDefaultTags.unittest]) as t:
        if t:
            unittest(['--enable-timing', '--very-verbose', '--suite', 'brainfck'])

    # Jackpot configuration is inherited from Truffle.
    with Task('Jackpot', tasks, tags=[BrainfckDefaultTags.jackpot]) as t:
        if t:
            jackpot(['--fail-on-warnings'], suite=None, nonZeroIsFatal=True)


def verify_ci(args):
    """Verify CI configuration"""
    mx.verify_ci(args, mx.suite('truffle'), _suite, 'common.json')


# REGISTER MX GATE RUNNER
#########################
add_gate_runner(_suite, _brainfck_gate_runner)

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Brainfck',
    short_name='bf',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['brainfck:BRAINFCK'],
    support_distributions=['brainfck:BRAINFCK_SUPPORT'],
    launcher_configs=[
        mx_sdk_vm.LanguageLauncherConfig(
            destination='bin/<exe:bf>',
            jar_distributions=['brainfck:BRAINFCK_LAUNCHER'],
            main_class='com.oracle.truffle.brainfck.launcher.BrainfckLauncher',
            build_args=['--language:bf'],
            language='bf',
        )
    ],
))


# Register new commands which can be used from the commandline with mx
mx.update_commands(_suite, {
    'bf': [_run_brainfck_launcher, '[args]'],
    'bf-standalone': [_run_brainfck_standalone, '[args]'],
    'verify-ci' : [verify_ci, '[options]'],
})

# Build configs
# pylint: disable=bad-whitespace
mx_sdk_vm.register_vm_config('bf-jvm',       ['java', 'nfi', 'sdk', 'tfl'                                        ], _suite, env_file='jvm')
mx_sdk_vm.register_vm_config('bf-jvm-ce',    ['java', 'nfi', 'sdk', 'tfl', 'cmp'                                 ], _suite, env_file='jvm-ce')
mx_sdk_vm.register_vm_config('bf-jvm-ee',    ['java', 'nfi', 'sdk', 'tfl', 'cmp', 'cmpee'                        ], _suite, env_file='jvm-ee')
mx_sdk_vm.register_vm_config('bf-native-ce', ['java', 'nfi', 'sdk', 'tfl', 'cmp'         , 'svm'         , 'tflm'], _suite, env_file='native-ce')
mx_sdk_vm.register_vm_config('bf-native-ee', ['java', 'nfi', 'sdk', 'tfl', 'cmp', 'cmpee', 'svm', 'svmee', 'tflm'], _suite, env_file='native-ee')
