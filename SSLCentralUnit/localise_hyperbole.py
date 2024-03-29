import numpy as np


class Point:
    def __init__(self, x, y):
        self.x = x
        self.y = y

    @property
    def norm2(self):
        return np.sqrt(np.float_power(self.x, 2) + np.float_power(self.y, 2))

    def __mul__(self, other):
        return Point(x=other * self.x,
                     y=other * self.y)

    def __add__(self, other):
        return Point(x=self.x + other.x,
                     y=self.y + other.y)

    def __sub__(self, other):
        return Point(x=self.x - other.x,
                     y=self.y - other.y)

    def __truediv__(self, n):
        return Point(x=self.x / n,
                     y=self.y / n)

    def __str__(self):
        return f'Point({self.x}, {self.y})'

    def copy(self):
        return Point(x=self.x,
                     y=self.y)

    def __repr__(self):
        return self.__str__()


# Global variables
c = 340.29  # speed of the sound (m/s)


def create_diff_dist(offsets):
    diff_dist = []
    for i in range(len(offsets) - 1):
        for j in range(i + 1, len(offsets)):
            diff_dist.append((offsets[i] - offsets[j]) * c)
    return diff_dist


def get_coordinates(offsets, sources):
    """
    apply algorithm Gauss_Newton : https://fr.wikipedia.org/wiki/Algorithme_de_Gauss-Newton

    :param offsets:
    :param sources:

    :return:
    """
    nb_sources = len(sources)
    nb_combinaisons = int(nb_sources * (nb_sources - 1) / 2)
    diff_dist = create_diff_dist(offsets)
    # Careful : If point is near a source, then some errors can occur (singular matrix)
    # TODO: make sure we don't start near a source
    coord = np.mean(sources)

    def combination_number(i, j):
        if i == j:
            raise Exception(f'i ({i}) can t be equal to j ({j})')
        i_ = min(i, j)
        j_ = max(i, j)
        res = int((nb_sources * i_) - (((i_ + 1) * i_) / 2) + j_ - i_ - 1)
        # print(f'i {i}, j {j}, nb sources {nb_sources}, combination number {res}')
        return res

    def f(coord):  # compute the matrix of the 3 position function (we want to solve f(x)=0)
        f = np.zeros(shape=(nb_combinaisons, 1))
        for i in range(0, nb_sources - 1):
            for j in range(i + 1, nb_sources):
                c_n = combination_number(i, j)
                f[c_n] = ((coord - sources[i]).norm2 - (coord - sources[j]).norm2) - diff_dist[c_n]
        return np.asmatrix(f)

    def get_jac(coord):
        """
        compute the corresponding jacobian matrix : https://fr.wikipedia.org/wiki/Matrice_jacobienne#targetText=En%20analyse%20vectorielle%2C%20la%20matrice,vient%20du%20math%C3%A9maticien%20Charles%20Jacobi.

        :param coord:

        :return:
        """
        jac = np.zeros(shape=(nb_combinaisons, 2))
        for i in range(0, nb_sources - 1):
            for j in range(i + 1, nb_sources):
                c_n = combination_number(i, j)
                jac[c_n, 0] = ((x - sources[i].x) / (coord - sources[i]).norm2) - (
                        (x - sources[j].x) / (coord - sources[j]).norm2)
                jac[c_n, 1] = ((y - sources[i].y) / (coord - sources[i]).norm2) - (
                            (y - sources[j].y) / (coord - sources[j]).norm2)
        return np.asmatrix(jac)

    for i in range(1000):
        jac = get_jac(coord)
        dp = (jac.T * jac) ** (-1) * jac.T * f(coord)
        coord -= Point(dp[0, 0], dp[1, 0])
    return coord


if __name__ == '__main__':
    # run some tests to verify

    # p coordinate :
    x, y = 2.5, 4
    point_p = Point(x, y)

    sources = [
        Point(0, 0),
        Point(5, 0),
        Point(0, 5)
    ]

    print(f'--- Test with P: {point_p}, and Sources: {sources} ---\n\n')

    # Real offsets
    offsets = [(sources[i] - point_p).norm2 / c for i in range(len(sources))]

    # latency in reception
    latency = np.random.uniform(0, 1)  # in [0, 1[ second
    offsets_latency = [t + latency for t in offsets]

    coord_comp = get_coordinates(offsets_latency, sources)
    d = (coord_comp - point_p).norm2
    print("With perfect time measures")
    print(f"theoretical coord: ({x},{y})")
    print(f"computed coord: ({coord_comp.x:.5f},{coord_comp.y:.5f})")
    print(f"error between positions: {d:.2f} meters")

    # Simulating errors on the times measured
    e = 0.001  # 1ms
    errors = [np.random.uniform(-e, e) for t in offsets]
    offsets_error = [offsets_latency[i] + errors[i] for i in range(len(offsets))]
    print(
        f"\nWith errors of 1ms on the times measured : {e} -> {errors}:\n\t{offsets_error} instead of {offsets_latency}")
    print(f"theoretical coord: ({x},{y})")
    coord_comp = get_coordinates(offsets_error, sources)
    d = (coord_comp - point_p).norm2
    print(f"computed coord: ({coord_comp.x:.5f},{coord_comp.y:.5f})")
    print(f"error between positions: {d:.2f} meters")
