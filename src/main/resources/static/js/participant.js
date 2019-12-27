const PARTICIPANT_MAIN_CLASS = 'participant main';
const PARTICIPANT_CLASS = 'participant';


function Participant(name) {
    this.name = name;

    let container = document.createElement('div');
    container.className = isPresentMainParticipant() ? PARTICIPANT_CLASS : PARTICIPANT_MAIN_CLASS;
    container.id = name;

    let span = document.createElement('span');
    let video = document.createElement('video');

    container.appendChild(video);
    container.appendChild(span);
    container.onclick = switchContainerClass;
    document.getElementById('participants').appendChild(container);

    span.appendChild(document.createTextNode(name));

    video.id = 'video-' + name;
    video.autoplay = true;
    video.controls = false;


    this.getVideoElement = function () {
        return video;
    };

    function switchContainerClass() {
        if (container.className === PARTICIPANT_CLASS) {
            let elements = Array.prototype.slice.call(document.getElementsByClassName(PARTICIPANT_MAIN_CLASS));
            elements.forEach(function (item) {
                item.className = PARTICIPANT_CLASS;
            });

            container.className = PARTICIPANT_MAIN_CLASS;
        } else {
            container.className = PARTICIPANT_CLASS;
        }
    }

    function isPresentMainParticipant() {
        return ((document.getElementsByClassName(PARTICIPANT_MAIN_CLASS)).length !== 0);
    }

    this.offerToReceiveVideo = function (error, offerSdp) {
        if (error) {
            console.error("sdp offer error");
            console.log('Invoking SDP offer callback function');
            return;
        }

        let msg = {
            id: "receiveVideoFrom",
            sender: name,
            sdpOffer: offerSdp
        };
        $.fn.sendMessage(msg);
    };


    this.onIceCandidate = function (candidate) {
        console.log("Local candidate" + JSON.stringify(candidate));

        let message = {
            id: 'onIceCandidate',
            candidate: candidate,
            name: name
        };
        $.fn.sendMessage(message);
    };

    Object.defineProperty(this, 'rtcPeer', {writable: true});

    this.dispose = function () {
        console.log('Disposing participant ' + this.name);
        this.rtcPeer.dispose();
        container.parentNode.removeChild(container);
    };
}
