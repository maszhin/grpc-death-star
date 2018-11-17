package ua.nedz.demo

import com.google.protobuf.Empty
import io.grpc.ManagedChannelBuilder
import io.grpc.internal.DnsNameResolverProvider
import io.grpc.util.RoundRobinLoadBalancerFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import ua.nedz.grpc.*

class DeathStarServiceImpl : DeathStarServiceGrpcKt.DeathStarServiceImplBase() {

    private val listeners = mutableListOf<Channel<PlanetProto.Planets>>()

    private var planetTarget: String? = System.getenv("PLANET_SERVICE_TARGET")
    private var scoreTarget: String? = System.getenv("SCORE_SERVICE_TARGET")
    private var logTarget: String? = System.getenv("LOG_SERVICE_TARGET")

    init {
        if (planetTarget.isNullOrEmpty()) planetTarget = "192.168.0.102:50061"
        if (scoreTarget.isNullOrEmpty()) scoreTarget = "192.168.0.102:50071"
        if (scoreTarget.isNullOrEmpty()) logTarget = "192.168.0.102:50081"
    }

    private val planetChannel = ManagedChannelBuilder
            .forTarget(planetTarget)
            .nameResolverFactory(DnsNameResolverProvider())
            .loadBalancerFactory(RoundRobinLoadBalancerFactory.getInstance())
            .usePlaintext(true)
            .build()
    private val planetStub = PlanetServiceGrpcKt.newStub(planetChannel)

    private val scoreChannel = ManagedChannelBuilder
            .forTarget(scoreTarget)
            .nameResolverFactory(DnsNameResolverProvider())
            .loadBalancerFactory(RoundRobinLoadBalancerFactory.getInstance())
            .usePlaintext(true)
            .build()
    private val scoreStub = ScoreServiceGrpcKt.newStub(scoreChannel)

    private val logChannel = ManagedChannelBuilder
            .forTarget(logTarget)
            .nameResolverFactory(DnsNameResolverProvider())
            .loadBalancerFactory(RoundRobinLoadBalancerFactory.getInstance())
            .usePlaintext(true)
            .build()
    private val logStub = LogServiceGrpcKt.newStub(logChannel)

    @ExperimentalCoroutinesApi
    override suspend fun destroy(requests: ReceiveChannel<PlanetProto.DestroyPlanetRequest>): ReceiveChannel<PlanetProto.Planets> {
        val channel = Channel<PlanetProto.Planets>()
        channel.send(planetStub.getAllPlanets(Empty.getDefaultInstance()))
        for (request in requests) {
            planetStub.removePlanet(PlanetServiceProto.RemovePlanetRequest
                    .newBuilder()
                    .setPlanetId(request.planetId)
                    .build())
            scoreStub.addScore(ScoreServiceProto.AddScoreRequest
                    .newBuilder()
                    .setUserId(request.userId)
                    .setToAdd(request.weight)
                    .build())
            logStub.destroyedPlanet(request)
            val newPlanet = planetStub.generateNewPlanet(Empty.getDefaultInstance())
            logStub.newPlanet(newPlanet)
            listeners.forEach { it.send(planetStub.getAllPlanets(Empty.getDefaultInstance())) }
        }
        return channel
    }

}