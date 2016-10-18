import traceback

from pyspookystuff.mav.comm import Connection, ProxyFactory
from pyspookystuff_test.mav import moveOut, APMSimContext, AbstractIT, endpoints

defaultProxyFactory = ProxyFactory()

def _move(point, proxyFactory=None):

    with APMSimContext():
        # type: (LocationGlobal, ProxyFactory) -> double, double
        # always move 100m.g

        conn = Connection.getOrCreate(
            endpoints,
            proxyFactory
        )

        vehicle = conn.vehicle

        moveOut(point, vehicle)

        return vehicle.location.local_frame.north, vehicle.location.local_frame.east

def move_NoProxy(tuple):
    return _move(tuple)
def move_Proxy(tuple):
    return _move(tuple, defaultProxyFactory)
class SimpleMoveIT(AbstractIT):

    @staticmethod
    def getFns():
        return [move_NoProxy, move_Proxy]