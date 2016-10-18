# Not part of the routing as its marginally useful outside testing.
from __future__ import print_function
import os

from dronekit import connect
from dronekit_sitl import SITL
from lazy import lazy

from pyspookystuff.mav import utils
from pyspookystuff import mav

# these are process-local and won't be shared by Spark workers

sitl_args = ['--model', 'quad', '--home=-35.363261,149.165230,584,353']

if 'SITL_SPEEDUP' in os.environ:
    sitl_args += ['--speedup', str(os.environ['SITL_SPEEDUP'])]
if 'SITL_RATE' in os.environ:
    sitl_args += ['-r', str(os.environ['SITL_RATE'])]


def tcp_master(instance):
    return 'tcp:127.0.0.1:' + str(5760 + instance*10)


usedINums = mav.mpManager.list()
class APMSim(object):
    global usedINums

    @staticmethod
    def nextINum():
        port = mav.utils.nextUnused(usedINums, range(0, 254))
        return port

    @staticmethod
    def create():
        index = APMSim.nextINum()
        try:
            result = APMSim(index)
            return result
        except Exception as ee:
            usedINums.remove(index)
            raise

    def __init__(self, iNum):
        # DO NOT USE! .create() is more stable
        # type: (int) -> None

        self.iNum = iNum
        self.args = sitl_args + ['-I' + str(iNum)]
        sitl = SITL()
        self._sitl = sitl
        sitl.download('copter', '3.3')
        sitl.launch(self.args, await_ready=True, restart=True)
        print("launching APM SITL .... PID=", str(sitl.p.pid))
        self.setParamAndRelaunch('SYSID_THISMAV', self.iNum + 1)

    def _getConnStr(self):
        return tcp_master(self.iNum)

    @lazy
    def connStr(self):
        return self._getConnStr()

    def setParamAndRelaunch(self, key, value):

        wd = self._sitl.wd
        v = connect(self._getConnStr(), wait_ready=True) # if use connStr will trigger cyclic invocation
        v.parameters.set(key, value, wait_ready=True)
        v.close()
        self._sitl.stop()
        self._sitl.launch(self.args, await_ready=True, restart=True, wd=wd, use_saved_data=True)
        v = connect(self._getConnStr(), wait_ready=True)
        # This fn actually rate limits itself to every 2s.
        # Just retry with persistence to get our first param stream.
        v._master.param_fetch_all()
        v.wait_ready()
        actualValue = v._params_map[key]
        assert actualValue == value
        v.close()

    def close(self):
        if self._sitl:
            print("Cleaning up APM SITL PID=", str(self._sitl.p.pid))
            self._sitl.stop()
        else:
            print("APM SITL not initialized, do not clean")

        try:
            usedINums.remove(self.iNum)
        except ValueError:
            pass

    def __del__(self):
        self.close()

